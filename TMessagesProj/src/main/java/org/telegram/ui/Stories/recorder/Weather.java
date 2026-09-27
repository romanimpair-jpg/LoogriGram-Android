package org.telegram.ui.Stories.recorder;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;

import java.util.TimeZone;

// LoogriGram: this also looked up the forecast for the story editor's weather
// sticker. Stories are not posted here; what is left is the state a weather
// sticker carries in a video and on a story.
public class Weather {

    public static boolean isDefaultCelsius() {
        final String timezone = TimeZone.getDefault().getID();
        return !(
            timezone.startsWith("US/") ||
            "America/Nassau".equals(timezone) ||
            "America/Belize".equals(timezone) ||
            "America/Cayman".equals(timezone) ||
            "Pacific/Palau".equals(timezone)
        );
    }

    public static class State extends TLObject {
        public double lat, lng;

//        public int type;
        public String emoji;
        public float temperature; // in celsius

        public String getEmoji() {
//            return Weather.getEmoji(type, lat, lng);
            return emoji;
        }

        public String getTemperature() {
            return getTemperature(isDefaultCelsius());
        }

        public String getTemperature(boolean celsius) {
            if (celsius) {
                return (int) Math.round(temperature) + "°C";
            } else {
                return (int) Math.round((this.temperature * 9.0 / 5.0) + 32) + "°F";
            }
        }

        public static Weather.State TLdeserialize(AbstractSerializedData stream) {
            Weather.State state = new Weather.State();
            state.lat = stream.readDouble(false);
            state.lng = stream.readDouble(false);
//            state.type = stream.readInt32(false);
            state.emoji = stream.readString(false);
            state.temperature = stream.readFloat(false);
            return state;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeDouble(lat);
            stream.writeDouble(lng);
//            stream.writeInt32(type);
            stream.writeString(emoji);
            stream.writeFloat(temperature);
        }
    }
}
