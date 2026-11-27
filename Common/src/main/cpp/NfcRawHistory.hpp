#pragma once

#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <map>
#include <mutex>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <utility>
#include <vector>

// Independent history: these records must never advance polls.dat, its counters,
// timing estimate, BLE duplicate cursor, or Abbott's parser state.
struct NfcRawSample {
  uint32_t time;
  int32_t id;
  uint32_t rawMgdlTimes10;
};
static_assert(sizeof(NfcRawSample) == 12);

class NfcRawHistory {
public:
  struct Result {
    size_t added = 0;
    size_t known = 0;
    bool persisted = false;
  };

  explicit NfcRawHistory(std::string path) : path_(std::move(path)) {}

  static bool valid(const NfcRawSample &sample) {
    return sample.id >= 0 && sample.id < static_cast<int32_t>(maxRecords) &&
           sample.time > 1598911200u && sample.time < 2145909600u &&
           sample.rawMgdlTimes10 > 380 && sample.rawMgdlTimes10 < 5020;
  }

  std::vector<NfcRawSample> snapshot() const {
    std::lock_guard<std::mutex> lock(fileMutex);
    std::vector<NfcRawSample> rows;
    if (!load(rows)) rows.clear();
    return rows;
  }

  Result append(const std::vector<NfcRawSample> &incoming) const {
    // This also serializes separate SensorGlucoseData instances for the same path.
    // Read before merging: no cached instance may overwrite another one's additions.
    std::lock_guard<std::mutex> lock(fileMutex);
    std::vector<NfcRawSample> previous;
    Result result;
    if (!load(previous)) return result; // Preserve a damaged/unreadable file.
    std::map<int32_t, NfcRawSample> rows;
    for (const auto &row : previous) rows.emplace(row.id, row);
    for (const auto &row : incoming) {
      if (!valid(row)) continue;
      if (rows.emplace(row.id, row).second) ++result.added;
      else ++result.known; // Same sensor minute, even if the scan clock drifted.
    }
    if (!result.added) {
      result.persisted = true;
      return result;
    }
    std::vector<NfcRawSample> merged;
    merged.reserve(rows.size());
    for (const auto &entry : rows) merged.push_back(entry.second);
    result.persisted = save(merged);
    if (!result.persisted) result.added = 0;
    return result;
  }

private:
  struct Header {
    uint32_t magic;
    uint32_t version;
    uint32_t count;
  };
  static_assert(sizeof(Header) == 12);
  static constexpr uint32_t magic = 0x3152464e; // NFR1
  static constexpr uint32_t maxRecords = 100000;
  inline static std::mutex fileMutex;
  const std::string path_;

  static bool readAll(int fd, void *buffer, size_t bytes) {
    auto *out = static_cast<unsigned char *>(buffer);
    while (bytes) {
      const auto count = ::read(fd, out, bytes);
      if (count < 0 && errno == EINTR) continue;
      if (count <= 0) return false;
      out += count;
      bytes -= static_cast<size_t>(count);
    }
    return true;
  }

  static bool writeAll(int fd, const void *buffer, size_t bytes) {
    auto *in = static_cast<const unsigned char *>(buffer);
    while (bytes) {
      const auto count = ::write(fd, in, bytes);
      if (count < 0 && errno == EINTR) continue;
      if (count <= 0) return false;
      in += count;
      bytes -= static_cast<size_t>(count);
    }
    return true;
  }

  bool load(std::vector<NfcRawSample> &rows) const {
    const int fd = ::open(path_.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return errno == ENOENT;
    struct stat info {};
    Header header {};
    bool ok = ::fstat(fd, &info) == 0 && readAll(fd, &header, sizeof(header)) &&
              header.magic == magic && header.version == 1 && header.count <= maxRecords &&
              info.st_size == static_cast<off_t>(sizeof(Header) + header.count * sizeof(NfcRawSample));
    if (ok) {
      rows.resize(header.count);
      ok = readAll(fd, rows.data(), rows.size() * sizeof(NfcRawSample));
      int32_t previous = -1;
      for (const auto &row : rows) {
        if (!valid(row) || row.id <= previous) { ok = false; break; }
        previous = row.id;
      }
    }
    ::close(fd);
    return ok;
  }

  bool save(const std::vector<NfcRawSample> &rows) const {
    if (rows.size() > maxRecords) return false;
    const std::string temporary = path_ + ".tmp";
    const int fd = ::open(temporary.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd < 0) return false;
    const Header header {magic, 1, static_cast<uint32_t>(rows.size())};
    bool ok = writeAll(fd, &header, sizeof(header)) &&
              writeAll(fd, rows.data(), rows.size() * sizeof(NfcRawSample)) && ::fsync(fd) == 0;
    if (::close(fd) != 0) ok = false;
    if (ok) ok = ::rename(temporary.c_str(), path_.c_str()) == 0;
    if (!ok) ::unlink(temporary.c_str());
    return ok;
  }
};
