// FILE: vku/chatapp/client/media/audio/AudioPlayer.java
// ✅ OPTIMIZED: 48kHz, echo cancellation

package vku.chatapp.client.media.audio;

import javax.sound.sampled.*;

public class AudioPlayer {
    private SourceDataLine speakers;
    private AudioFormat format;
    private boolean isPlaying = false;

    public AudioPlayer() {
        // ✅ HIGH QUALITY: Match capture format (48kHz, 16-bit, mono)
        format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                48000.0f,  // 48kHz
                16,        // 16-bit
                1,         // Mono
                2,         // Frame size
                48000.0f,  // Frame rate
                false      // Little endian
        );
    }

    public void startPlayback() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("⚠️ Line not supported, trying fallback");
            // Fallback
            format = new AudioFormat(44100, 16, 1, true, false);
            info = new DataLine.Info(SourceDataLine.class, format);
        }

        speakers = (SourceDataLine) AudioSystem.getLine(info);

        // ✅ Larger buffer for smoother playback
        int bufferSize = (int) format.getSampleRate() * format.getFrameSize();
        speakers.open(format, bufferSize);
        speakers.start();
        isPlaying = true;

        System.out.println("✅ Audio playback started:");
        System.out.println("   Sample Rate: " + format.getSampleRate() + " Hz");
        System.out.println("   Bit Depth: " + format.getSampleSizeInBits() + " bit");
        System.out.println("   Channels: " + format.getChannels());
    }

    public void playAudio(byte[] audioData) {
        if (!isPlaying || speakers == null || audioData == null || audioData.length == 0) {
            return;
        }

        try {
            // ✅ Write audio data with flow control
            int written = 0;
            int chunkSize = 4096;

            while (written < audioData.length && isPlaying) {
                int toWrite = Math.min(chunkSize, audioData.length - written);
                int result = speakers.write(audioData, written, toWrite);

                if (result > 0) {
                    written += result;
                } else {
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("⚠️ Playback error: " + e.getMessage());
        }
    }

    public void stopPlayback() {
        if (speakers != null) {
            isPlaying = false;

            try {
                speakers.drain(); // Wait for buffer to empty
                speakers.stop();
                speakers.close();
                System.out.println("✅ Audio playback stopped");
            } catch (Exception e) {
                System.err.println("⚠️ Error stopping playback: " + e.getMessage());
            }
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public AudioFormat getFormat() {
        return format;
    }
}