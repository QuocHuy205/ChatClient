package vku.chatapp.client.media.audio;

import javax.sound.sampled.*;

public class AudioCapture {
    private TargetDataLine microphone;
    private AudioFormat format;
    private boolean isCapturing = false;

    public AudioCapture() {
        // 16-bit, 44.1kHz, mono
        format = new AudioFormat(44100, 16, 1, true, false);
    }

    public void startCapture() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        microphone.start();
        isCapturing = true;

        System.out.println("Audio capture started");
    }

    public byte[] captureAudio(int bufferSize) {
        if (!isCapturing || microphone == null) {
            return new byte[0];
        }

        byte[] buffer = new byte[bufferSize];
        int bytesRead = microphone.read(buffer, 0, buffer.length);

        if (bytesRead > 0) {
            byte[] audioData = new byte[bytesRead];
            System.arraycopy(buffer, 0, audioData, 0, bytesRead);
            return audioData;
        }

        return new byte[0];
    }

    public void stopCapture() {
        if (microphone != null) {
            isCapturing = false;
            microphone.stop();
            microphone.close();
            System.out.println("Audio capture stopped");
        }
    }

    public boolean isCapturing() {
        return isCapturing;
    }
}