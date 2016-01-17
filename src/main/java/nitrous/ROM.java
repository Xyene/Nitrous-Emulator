package nitrous;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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

    public static File[] merge(File[] listA, File[] listB, HashMap<File, Integer> frequency)
    {
        File[] result = new File[listA.length + listB.length];
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

            if (frequency.get(listA[indexA]) > frequency.get(listB[indexB]))
                result[index++] = listA[indexA++];
            else
                result[index++] = listB[indexB++];
        }
        return result;
    }

    public static File[] mergeSort(File[] list, HashMap<File, Integer> frequency)
    {
        if (list.length <= 1)
            return list;

        int mid = list.length / 2;
        File[] a = new File[mid];
        File[] b = new File[list.length - mid];

        System.arraycopy(list, 0, a, 0, mid);
        System.arraycopy(list, mid, b, 0, b.length);

        a = mergeSort(a, frequency);
        b = mergeSort(b, frequency);

        return merge(a, b, frequency);
    }

    public File[] mostUsed(int count)
    {
        ArrayList<File> roms = new ArrayList<>();
        HashMap<File, Integer> frequency = new HashMap<>();

        try
        {
            for (String rom : store.keys())
            {
                File file = new File(rom);
                if (file.isFile())
                {
                    roms.add(file);
                    frequency.put(file, store.getInt(rom, 0));
                } else
                    store.remove(rom);
            }
        } catch (BackingStoreException e)
        {
            return new File[0];
        }

        File[] sorted = mergeSort(roms.toArray(new File[roms.size()]), frequency);
        System.out.println(Arrays.toString(sorted));
        if (sorted.length <= count)
            return sorted;

        File[] result = new File[count];
        System.arraycopy(sorted, 0, result, 0, count);
        return result;
    }
}
