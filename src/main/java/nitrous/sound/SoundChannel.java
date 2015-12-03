package nitrous.sound;

import nitrous.Emulator;

import javax.sound.sampled.AudioFormat;

public abstract class SoundChannel
{
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100, 16, 2, true, true);

    protected final Emulator core;

    public SoundChannel(Emulator core) {
        this.core = core;
    }

    public static int gbFreqToCycles(int gbFreq) {
        return 32 * (2048 - gbFreq);
    }

    private boolean updateRequest = false;
    private boolean restartRequest = false;

    public void update() {
        updateRequest = true;
    }

    public void restart() {
        restartRequest = true;
    }

    protected boolean handleRequests() {
        boolean didSomething = updateRequest || restartRequest;

        if (updateRequest)
            handleUpdateRequest();

        if (restartRequest)
            handleRestartRequest();

        updateRequest = restartRequest = false;

        return didSomething;
    }

    protected abstract void handleRestartRequest();
    protected abstract void handleUpdateRequest();

    public abstract int render();
}
