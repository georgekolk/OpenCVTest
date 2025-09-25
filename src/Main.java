import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.util.Random;
import javax.sound.sampled.*;
import javax.swing.*;

public class Main {

    static boolean reeled = false;
    static boolean exitFlag = false;
    static int inputDevice = 0; // 1 - default speakers, 2 - VoiceMeeter

    public static void main(String[] args) throws AWTException, IOException, InterruptedException, LineUnavailableException {
        OpenCV.loadLocally();
        Robot robot = new Robot();

        Mat template = Imgcodecs.imread("bobber.png", Imgproc.COLOR_BGR2GRAY);
        Point matchLoc = findFishingBobber(robot, template);
        //reelIn(robot);

        Rect region = new Rect(
                (int) matchLoc.x,
                (int) matchLoc.y,
                template.width(),
                template.height()
        );

        System.out.println("region: " + region);

        while (true) {
            // Робимо скріншот тільки цієї області
            BufferedImage screenPart = robot.createScreenCapture(new Rectangle(region.x, region.y, region.width, region.height));
            File regionScreenFile = new File("region_screenshot.png");
            ImageIO.write(screenPart, "png", regionScreenFile);
            Mat imgPart = Imgcodecs.imread("region_screenshot.png", Imgproc.COLOR_BGR2GRAY);;

            // Порівняння з оригінальним шаблоном
            Mat resultPart = new Mat();
            Imgproc.matchTemplate(imgPart, template, resultPart, Imgproc.TM_CCOEFF_NORMED);
            Core.MinMaxLocResult mmrPart = Core.minMaxLoc(resultPart);

            // Якщо значення схожості впало → об’єкт змінився
            if (mmrPart.maxVal < 0.6) {
                System.out.println("Об’єкт у цій області змінився або зник!");
                break;
            }

            Thread.sleep(500); // перевірка раз на 0.5 сек
        }
    }

    public static void reelIn(Robot robot) throws LineUnavailableException, InterruptedException, IOException {
        int secondsTimer = 0;
        Random random = new Random();

        while (!exitFlag) {
            reeled = false;

            // Get audio input line
            Mixer.Info mixerInfo = AudioSystem.getMixerInfo()[5];
            System.out.println("mixerInfo: " + mixerInfo.getName() + " " + mixerInfo.getDescription());

            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            AudioFormat format = new AudioFormat(44100, 16, 2, true, true);
            //AudioFormat format = new AudioFormat(48000, 24, 2, true, true);

            //TargetDataLine line = (TargetDataLine) mixer.getLine(new DataLine.Info(TargetDataLine.class, format));
            TargetDataLine line = AudioSystem.getTargetDataLine(format);
            line.open(format);
            line.start();

            byte[] buffer = new byte[48000 * 2]; // 16 bit = 2 bytes
            int bytesRead = line.read(buffer, 0, buffer.length);

            ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
            // Загортаємо в AudioInputStream (frame = 2 байти для mono 16bit)
            AudioInputStream ais = new AudioInputStream(bais, format, buffer.length / format.getFrameSize());
            // Зберігаємо у WAV
            File wavFile = new File("output.wav");
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavFile);


            double audioPeak = 0;
            for (int i = 0; i < bytesRead; i += 2) {
                short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xff));
                audioPeak = Math.max(audioPeak, Math.abs(sample) / 32768.0);
            }

            secondsTimer++;

            if (audioPeak > 0.06) {
                System.out.println("<< You (hopefully) caught something! >>\n");
                // Simulate right mouse click
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                Thread.sleep((long) (30 + random.nextInt(50))); // 0.03 - 0.08 sec
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                Thread.sleep((long) (350 + random.nextInt(150))); // 0.35 - 0.5 sec
                reeled = true;
                break;
            }

            if (secondsTimer > 12) {
                System.out.println("<< Failed. Trying again. >>");
                break;
            }

            line.stop();
            line.close();
        }
    }

    private static Point findFishingBobber(Robot robot, Mat template) throws InterruptedException, IOException {
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage screenFullImage = robot.createScreenCapture(screenRect);
        File screenFile = new File("screenshot.png");
        ImageIO.write(screenFullImage, "png", screenFile);

        // 2. Завантажуємо зображення екрану і шаблону
        Mat img = Imgcodecs.imread("screenshot.png", Imgproc.COLOR_BGR2GRAY);
        //Mat template = Imgcodecs.imread("bobber.png", Imgproc.COLOR_BGR2GRAY); // шаблон який шукаємо

        // 3. Template Matching
        int matchMethod = Imgproc.TM_CCOEFF_NORMED;
        Mat result = new Mat();
        Imgproc.matchTemplate(img, template, result, matchMethod);

        Core.MinMaxLocResult mmr = Core.minMaxLoc(result);

        Point matchLoc = null;

        if (mmr.maxVal >= 0.6) { // поріг збігу
            matchLoc = mmr.maxLoc;
            System.out.println("Знайдено на екрані в координатах: " + matchLoc);


            /*Mat imgDisplay = img.clone();

            // Малюємо прямокутник навколо збігу
            Imgproc.rectangle(
                    imgDisplay,
                    matchLoc,
                    new Point(matchLoc.x + template.width(), matchLoc.y + template.height()),
                    new Scalar(0, 255, 0), // зелений колір
                    2                     // товщина лінії
            );
            BufferedImage imgOut = matToBufferedImage(imgDisplay );
            JFrame frame = new JFrame("Result");
            frame.getContentPane().add(new JLabel(new ImageIcon(imgOut)));
            frame.pack();
            frame.setVisible(true);*/

            // 4. Рухаємось мишкою в це місце
            robot.mouseMove((int)matchLoc.x + template.width()/2,
                    (int)matchLoc.y + template.height()/2);

            Thread.sleep(2000);
        } else {
            System.out.println("Не знайдено.");
        }
        return matchLoc;
    }

    public static BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        byte[] b = new byte[mat.channels() * mat.cols() * mat.rows()];
        mat.get(0, 0, b);
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        image.getRaster().setDataElements(0, 0, mat.cols(), mat.rows(), b);
        return image;
    }

    public static Mat bufferedImageToMat(BufferedImage bi) {
        int type = bi.getType() == BufferedImage.TYPE_BYTE_GRAY ? CvType.CV_8UC1 : CvType.CV_8UC3;
        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), type);
        byte[] data = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        return mat;
    }
}
