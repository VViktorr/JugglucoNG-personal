package com.eveningoutpost.dexdrip.services.broadcastservice.models;

import android.app.Application;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 33, 34}, application = Application.class)
public class GraphLineParcelTest {
    @Test
    @SuppressWarnings("deprecation") // Read back using the legacy client API.
    public void readsLegacyGraphPointListAndPreservesParcelLayout() {
        Parcel input = Parcel.obtain();
        Parcel output = Parcel.obtain();
        try {
            input.writeList(List.of(new GraphPoint(4.5f, 7.25f)));
            input.writeInt(0xff123456);
            input.setDataPosition(0);

            GraphLine line = GraphLine.CREATOR.createFromParcel(input);
            assertEquals(1, line.values.size());
            assertEquals(4.5f, line.values.get(0).x, 0f);
            assertEquals(7.25f, line.values.get(0).y, 0f);

            line.writeToParcel(output, 0);
            output.setDataPosition(0);
            List<?> points = output.readArrayList(GraphPoint.class.getClassLoader());
            assertEquals(1, points.size());
            assertEquals(7.25f, GraphPoint.class.cast(points.get(0)).y, 0f);
            assertEquals(0xff123456, output.readInt());
        } finally {
            input.recycle();
            output.recycle();
        }
    }
}
