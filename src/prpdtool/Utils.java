package prpdtool;

/**
 *
 * @author jstar
 */
import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class Utils {

    public static String readLastLineUtf8(String file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {

            long len = raf.length();
            long pos = len - 1;

            if (pos < 0) {
                return "";
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // pomiń końcowe newline
            while (pos >= 0) {
                raf.seek(pos);
                int c = raf.read();
                if (c != '\n' && c != '\r') {
                    break;
                }
                pos--;
            }

            while (pos >= 0) {
                raf.seek(pos);
                int c = raf.read();

                if (c == '\n' || c == '\r') {
                    break;
                }

                baos.write(c);
                pos--;
            }

            byte[] bytes = baos.toByteArray();

            // odwrócenie bajtów
            for (int i = 0; i < bytes.length / 2; i++) {
                byte tmp = bytes[i];
                bytes[i] = bytes[bytes.length - 1 - i];
                bytes[bytes.length - 1 - i] = tmp;
            }

            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "1 1";
        }
    }

    public static String readLastPair(String path) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(path, "r"); FileChannel ch = raf.getChannel()) {

            long size = ch.size();
            if (size < 16) {
                throw new IllegalArgumentException("Plik za krótki");
            }

            long pos = size - 16; // ostatnie 2 double

            ByteBuffer buf = ByteBuffer.allocate(16);
            buf.order(ByteOrder.LITTLE_ENDIAN);

            ch.read(buf, pos);
            buf.flip();

            double t = buf.getDouble();
            double u = buf.getDouble();

            return t + " " + u;
        }
    }
}
