package tk.glucodata

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Libre2Gen1FrameAssemblerTest {
    @Test
    fun assemblesOneFrameAndDiscardsFragmentsFromTheRetiredLink() {
        val frame = ByteArray(46) { it.toByte() }
        val assembler = Libre2Gen1FrameAssembler()
        assertNull(assembler.append(frame.copyOfRange(0, 20)))
        assembler.reset()
        assertNull(assembler.append(frame.copyOfRange(20, 38)))
        assertNull(assembler.append(frame.copyOfRange(38, 46)))
        assertNull(assembler.append(frame.copyOfRange(0, 20)))
        assertNull(assembler.append(frame.copyOfRange(20, 38)))
        assertArrayEquals(frame, assembler.append(frame.copyOfRange(38, 46)))
        assertNull(assembler.append(frame.copyOfRange(38, 46)))
    }
}
