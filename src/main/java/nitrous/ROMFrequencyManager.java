package nitrous;

import java.io.File;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Most frequently used ROMs manager.
 */
public class ROMFrequencyManager
{
    /**
     * Frequency backing store: with {@link Preferences}.
     */
    private final Preferences store;

    /**
     * File name backing store: with {@link Preferences}.
     */
    private final Preferences fileNameStore;

    /**
     * Create a ROM freqeuency manager from its backing store: {@link Preferences}.
     *
     * @param store the {@link Preferences} to store the data.
     */
    public ROMFrequencyManager(Preferences store)
    {
        this.store = store;
        fileNameStore = store.node("fileName");
    }

    /**
     * Utility method to MD5 hash a {@link String}.
     *
     * @param input a string to hash
     * @return the hexadecimal hash output
     */
    public static String md5String(String input) {
        byte[] digest;

        // Use Java's crypto library to hash.
        try
        {
            digest = MessageDigest.getInstance("MD5").digest(input.getBytes());
        } catch (NoSuchAlgorithmException e)
        {
            // This can't really happen.
            throw new RuntimeException(e);
        }

        // Use BigInteger to convert binary data into hexadecimal.
        BigInteger bi = new BigInteger(1, digest);
        return String.format("%0" + (digest.length << 1) + "X", bi);
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

        // Hash the string to avoid length limit
        String hash = md5String(path);

        // Store real path
        fileNameStore.put(hash, path);

        // Increase the usage frequency.
        store.putInt(hash, store.getInt(hash, 0) + 1);
    }//end usedROM

    /**
     * Merges two sorted lists of File objects into one based on frequency.
     *
     * @param listA     the first list
     * @param listB     the second list
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
            }//end if
            if (indexB == listB.length)
            {
                System.arraycopy(listA, indexA, result, index, listA.length - indexA);
                break;
            }//end if

            // Otherwise, put the element with greater frequency to the output list.
            if (frequency.get(listA[indexA]) > frequency.get(listB[indexB]))
                result[index++] = listA[indexA++];
            else
                result[index++] = listB[indexB++];
        }//end while

        return result;
    }//end merge

    /**
     * A mergesort implementation that would sort a list of {@link File} objects by frequency.
     *
     * @param list      the list to sort
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
    }//end mergeSort

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
            for (String hash : store.keys())
            {
                // Get the ROM file name from the hash.
                String rom = fileNameStore.get(hash, null);

                // Remove the hash entry if it somehow wasn't mapped to a name.
                if (rom == null)
                {
                    store.remove(hash);
                    continue;
                }

                // Create file object based on path.
                File file = new File(rom);

                // Only add information about ROMs that still exist.
                if (file.isFile())
                {
                    roms.add(file);
                    frequency.put(file, store.getInt(hash, 0));
                } else
                {
                    // Remove information about deleted ROMs.
                    store.remove(hash);
                    fileNameStore.remove(hash);
                }//end if
            }//end for
        } catch (BackingStoreException e)
        {
            // If failed to query ROMs, return an empty list.
            return new File[0];
        }//end try

        // Sort the ROMs by frequency.
        File[] sorted = mergeSort(roms.toArray(new File[roms.size()]), frequency);

        // Return if we don't need to pick off the first count elements.
        if (sorted.length <= count)
            return sorted;

        // Return the first count elements.
        File[] result = new File[count];
        System.arraycopy(sorted, 0, result, 0, count);
        return result;
    }//end mostUsed
}//end class ROMFrequencyManager
