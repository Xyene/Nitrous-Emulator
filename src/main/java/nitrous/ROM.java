package nitrous;

import java.util.HashMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class ROM
{
    private final Preferences store;

    public ROM(Preferences store)
    {
        this.store = store;
    }

    public void usedROM(String path)
    {
        store.putInt(path, store.getInt(path, 0) + 1);
    }

    public static String[] merge(String[] listA, String[] listB, HashMap<String, Integer> frequency)
    {
        String[] result = new String[listA.length + listB.length];
        int indexA = 0, indexB = 0, index = 0;
        while (true)
        {
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
            if (frequency.get(listA[indexA]) < frequency.get(listB[indexB]))
                result[index++] = listA[indexA++];
            else
                result[index++] = listB[indexB++];
        }
        return result;
    }

    public static String[] mergeSort(String[] list, HashMap<String, Integer> frequency)
    {
        if (list.length == 1)
            return list;
        int mid = list.length / 2;
        String[] a = new String[mid];
        String[] b = new String[list.length-mid];
        System.arraycopy(list, 0, a, 0, mid);
        System.arraycopy(list, mid, b, 0, b.length);
        a = mergeSort(a, frequency);
        b = mergeSort(b, frequency);

        return merge(a, b, frequency);
    }

    public String[] mostUsed(int count)
    {
        String[] roms;

        try
        {
            roms = store.keys();
        } catch (BackingStoreException e)
        {
            return new String[0];
        }

        HashMap<String, Integer> frequency = new HashMap<>();
        for (String rom : roms)
            frequency.put(rom, store.getInt(rom, 0));

        String[] sorted = mergeSort(roms, frequency);
        if (sorted.length <= count)
            return sorted;

        roms = new String[count];
        System.arraycopy(sorted, 0, roms, 0, roms.length);
        return roms;
    }
}
