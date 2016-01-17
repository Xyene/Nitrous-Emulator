package nitrous;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Most frequently used ROMs manager.
 */
public class ROM
{
    /**
     * Backing store: with {@link Preferences}.
     */
    private final Preferences store;

    /**
     * Create a ROM freqeuency manager from its backing store: {@link Preferences}.
     *
     * @param store the {@link Preferences} to store the data.
     */
    public ROM(Preferences store)
    {
        this.store = store;
    }

    /**
     * Increase the usage frequency of a ROM.
     *
     * @param file the {@link File} object that represents the file.
     */
    public void usedROM(File file)
    {
        // Used the absolute path to identify the file.
        String path = file.getAbsolutePath();

        // Increase the usage frequency.
        store.putInt(path, store.getInt(path, 0) + 1);
    }

    /**
     * Merges two sorted lists of File objects into one based on frequency.
     *
     * @param listA the first list
     * @param listB the second list
     * @param frequency the frequency map by which the lists shall be sorted
     * @return a newly created list, containing everything in listA and listB, sorted
     */
    public static File[] merge(File[] listA, File[] listB, HashMap<File, Integer> frequency)
    {
        // Create output array.
        File[] result = new File[listA.length + listB.length];

        // Indices into the arrays
        int indexA = 0, indexB = 0, index = 0;

        // Loop until both arrays are exhausted.
        while (true)
        {
            // If one array is exhausted, append the rest of the other array into the output.
            if (indexA == listA.length)
            {
                System.arraycopy(listB, indexB, result, index, listB.length - indexB);
                break;
            }
            if (indexB == listB.length)
            {
                System.arraycopy(listA, indexA, result, index, listA.length - indexA);
                break;
            }

            // Otherwise, put the element with greater frequency to the output list.
            if (frequency.get(listA[indexA]) > frequency.get(listB[indexB]))
                result[index++] = listA[indexA++];
            else
                result[index++] = listB[indexB++];
        }

        return result;
    }

    /**
     * A mergesort implementation that would sort a list of {@link File} objects by frequency.
     *
     * @param list the list to sort
     * @param frequency the frequency map
     * @return a new list that contains elements of list, in order of frequency
     */
    public static File[] mergeSort(File[] list, HashMap<File, Integer> frequency)
    {
        // Zero or one sized arrays are already sorted.
        if (list.length <= 1)
            return list;

        // Find mid point, partition arrays.
        int mid = list.length / 2;
        File[] a = new File[mid];
        File[] b = new File[list.length - mid];

        // Copy data into partitioned arrays.
        System.arraycopy(list, 0, a, 0, mid);
        System.arraycopy(list, mid, b, 0, b.length);

        // Recursively sort both partitions.
        a = mergeSort(a, frequency);
        b = mergeSort(b, frequency);

        // Merge the two partitions.
        return merge(a, b, frequency);
    }

    /**
     * Find the {@literal count} most used ROMs.
     *
     * @param count the maximum number of ROMs to return
     * @return a list of ROMs in order of usage frequency
     */
    public File[] mostUsed(int count)
    {
        // Create ROM list and frequency map.
        ArrayList<File> roms = new ArrayList<>();
        HashMap<File, Integer> frequency = new HashMap<>();

        // Populate ROM list and frequency map.
        try
        {
            // Look at every known ROM.
            for (String rom : store.keys())
            {
                // Create file object based on path.
                File file = new File(rom);

                // Only add information about ROMs that still exist.
                if (file.isFile())
                {
                    roms.add(file);
                    frequency.put(file, store.getInt(rom, 0));
                } else
                {
                    // Remove information about deleted ROMs.
                    store.remove(rom);
                }
            }
        } catch (BackingStoreException e)
        {
            // If failed to query ROMs, return an empty list.
            return new File[0];
        }

        // Sort the ROMs by frequency.
        File[] sorted = mergeSort(roms.toArray(new File[roms.size()]), frequency);

        // Return if we don't need to pick off the first count elements.
        if (sorted.length <= count)
            return sorted;

        // Return the first count elements.
        File[] result = new File[count];
        System.arraycopy(sorted, 0, result, 0, count);
        return result;
    }
}
