package vku.chatapp.client.media.audio;

import javax.sound.sampled.*;

public class AudioPlayer {
    private SourceDataLine speakers;
    private AudioFormat format;
    private boolean isPlaying = false;

    public AudioPlayer() {
        // Match AudioCapture format
        format = new AudioFormat(44100, 16, 1, true, false);
    }

    public void startPlayback() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        speakers = (SourceDataLine) AudioSystem.getLine(info);
        speakers.open(format);
        speakers.start();
        isPlaying = true;

        System.out.println("Audio playback started");
    }

    public void playAudio(byte[] audioData) {
        if (!isPlaying || speakers == null || audioData == null || audioData.length == 0) {
            return;
        }

        speakers.write(audioData, 0, audioData.length);
    }

    public void stopPlayback() {
        if (speakers != null) {
            isPlaying = false;
            speakers.drain();
            speakers.stop();
            speakers.close();
            System.out.println("Audio playback stopped");
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}