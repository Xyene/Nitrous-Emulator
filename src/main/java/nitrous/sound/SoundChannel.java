package nitrous.sound;

import nitrous.cpu.Emulator;

import javax.sound.sampled.AudioFormat;

/**
 * Abstract class that aids in the implementation of a sound channel.
 */
public abstract class SoundChannel
{
    /**
     * The field to store the {@link Emulator} instance.
     */
    protected final Emulator core;

    /**
     * Base constructor, stores the {@link Emulator} instance.
     *
     * @param core
     */
    public SoundChannel(Emulator core)
    {
        this.core = core;
    }

    /**
     * Field to indicate there is an update request.
     * Will update the sound on next available opportunity.
     */
    private boolean updateRequest = false;

    /**
     * Field to indicate there is an restart request.
     * Will restart the sound on next available opportunity.
     */
    private boolean restartRequest = false;

    /**
     * Called to update the sound: set flag.
     */
    public void update()
    {
        updateRequest = true;
    }

    /**
     * Called to restart the sound: set flag.
     */
    public void restart()
    {
        restartRequest = true;
    }

    /**
     * Method to handle requests.
     *
     * @return whether there was any changes, i.e. an update or restart.
     */
    protected boolean handleRequests()
    {
        // We did something if there was a request of either kind.
        boolean didSomething = updateRequest || restartRequest;

        // If there is an update request, handle it.
        if (updateRequest)
            handleUpdateRequest();

        // If there is an restart request, handle it.
        if (restartRequest)
            handleRestartRequest();

        // Requests are handled and therefore no longer exist.
        updateRequest = restartRequest = false;

        // Return whether there was any requests, before we set the variables to false.
        return didSomething;
    }

    /**
     * Method called to handle a restart request.
     */
    protected abstract void handleRestartRequest();

    /**
     * Method called to handle an update request.
     */
    protected abstract void handleUpdateRequest();

    /**
     * Renders one sample of sound.
     *
     * @return the sample, as a signed integer.
     */
    public abstract int render();

    /**
     * The {@link AudioFormat} that we use: 44100 Hz, signed 16 bits, stereo, big-endian.
     */
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100, 16, 2, true, true);

    /**
     * Utility function to convert Gameboy frequency values into CPU cycles.
     *
     * @param gbFreq Gameboy frequency value
     * @return CPU cycles
     */
    public static int gbFreqToCycles(int gbFreq)
    {
        return 32 * (2048 - gbFreq);
    }
}
