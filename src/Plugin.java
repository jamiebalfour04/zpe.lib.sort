import jamiebalfour.HelperFunctions;
import jamiebalfour.zpe.core.YASSByteCodes;
import jamiebalfour.zpe.core.ZPEFunction;
import jamiebalfour.zpe.core.ZPERuntimeEnvironment;
import jamiebalfour.zpe.core.ZPEStructure;
import jamiebalfour.zpe.exceptions.ZPERuntimeException;
import jamiebalfour.zpe.interfaces.ZPECustomFunction;
import jamiebalfour.zpe.interfaces.ZPELibrary;
import jamiebalfour.zpe.interfaces.ZPEType;
import jamiebalfour.zpe.types.ZPEBoolean;
import jamiebalfour.zpe.types.ZPEList;
import jamiebalfour.zpe.types.ZPEMap;

import java.text.Collator;
import java.util.*;

/**
 * zpe.lib.sort
 *
 * Global functions:
 *  - sort(list items[, string mode][, boolean descending]) => list | false
 *  - sort_by(list items, string key[, string mode][, boolean descending]) => list | false
 *  - sort_map_keys(map m[, string mode][, boolean descending]) => list | false
 *  - sort_map_values(map m[, string mode][, boolean descending]) => list | false
 *
 * Modes:
 *  - "auto" (default)
 *  - "number"
 *  - "string"
 *  - "string_ci"
 *  - "natural"
 */
public class Plugin implements ZPELibrary {

  @Override
  public Map<String, ZPECustomFunction> getFunctions() {
    HashMap<String, ZPECustomFunction> arr = new HashMap<>();
    arr.put("sort", new Sort());
    arr.put("sort_by", new SortBy());
    arr.put("sort_map_keys", new SortMapKeys());
    arr.put("sort_map_values", new SortMapValues());
    return arr;
  }

  @Override
  public Map<String, Class<? extends ZPEStructure>> getObjects() {
    return null;
  }

  @Override public boolean supportsWindows() { return true; }
  @Override public boolean supportsMacOs() { return true; }
  @Override public boolean supportsLinux() { return true; }

  @Override
  public String getName() {
    return "libSort";
  }

  @Override
  public String getVersionInfo() {
    return "1.0";
  }

  // ---------------------- Helpers ----------------------

  private enum Mode { AUTO, NUMBER, STRING, STRING_CI, NATURAL }

  private static Mode parseMode(Object o) {
    if (o == null) return Mode.AUTO;
    String s = o.toString().trim().toLowerCase(Locale.ROOT);
    switch (s) {
      case "auto": return Mode.AUTO;
      case "number": return Mode.NUMBER;
      case "string": return Mode.STRING;
      case "string_ci":
      case "ci":
      case "case_insensitive":
        return Mode.STRING_CI;
      case "natural": return Mode.NATURAL;
      default: return Mode.AUTO;
    }
  }

  private static boolean parseDescending(Object o) {
    if (o == null) return false;
    // Accept ZPEBoolean, "true"/"false", "1"/"0"
    String s = o.toString().trim().toLowerCase(Locale.ROOT);
    return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y");
  }

  private static Double tryParseNumber(String s) {
    try {
      // HelperFunctions.stringToDouble might exist; keep safe
      return Double.parseDouble(s.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private static int naturalCompare(String a, String b, boolean caseInsensitive) {
    if (a == null) a = "";
    if (b == null) b = "";
    int ia = 0, ib = 0;
    int na = a.length(), nb = b.length();

    while (ia < na || ib < nb) {
      if (ia >= na) return -1;
      if (ib >= nb) return 1;

      char ca = a.charAt(ia);
      char cb = b.charAt(ib);

      boolean da = Character.isDigit(ca);
      boolean db = Character.isDigit(cb);

      if (da && db) {
        int sa = ia;
        int sb = ib;

        while (ia < na && Character.isDigit(a.charAt(ia))) ia++;
        while (ib < nb && Character.isDigit(b.charAt(ib))) ib++;

        String pa = a.substring(sa, ia);
        String pb = b.substring(sb, ib);

        // Compare as integers (length first to avoid BigInteger dependency)
        pa = stripLeadingZeros(pa);
        pb = stripLeadingZeros(pb);
        if (pa.length() != pb.length()) return Integer.compare(pa.length(), pb.length());

        int cmp = pa.compareTo(pb);
        if (cmp != 0) return cmp;
      } else {
        char xa = ca;
        char xb = cb;
        if (caseInsensitive) {
          xa = Character.toLowerCase(xa);
          xb = Character.toLowerCase(xb);
        }
        if (xa != xb) return Character.compare(xa, xb);
        ia++;
        ib++;
      }
    }
    return 0;
  }

  private static String stripLeadingZeros(String s) {
    int i = 0;
    while (i < s.length() - 1 && s.charAt(i) == '0') i++;
    return s.substring(i);
  }

  private static Comparator<ZPEType> valueComparator(Mode mode) {
    final Collator collator = Collator.getInstance(Locale.UK);
    collator.setStrength(Collator.TERTIARY);

    switch (mode) {
      case NUMBER:
        return (a, b) -> {
          Double da = tryParseNumber(String.valueOf(a));
          Double db = tryParseNumber(String.valueOf(b));
          if (da == null && db == null) return 0;
          if (da == null) return 1;   // non-numbers go last
          if (db == null) return -1;
          return Double.compare(da, db);
        };

      case STRING:
        return (a, b) -> collator.compare(String.valueOf(a), String.valueOf(b));

      case STRING_CI:
        return (a, b) -> collator.compare(String.valueOf(a).toLowerCase(Locale.ROOT),
                String.valueOf(b).toLowerCase(Locale.ROOT));

      case NATURAL:
        return (a, b) -> naturalCompare(String.valueOf(a), String.valueOf(b), false);

      case AUTO:
      default:
        return (a, b) -> {
          String sa = String.valueOf(a);
          String sb = String.valueOf(b);

          Double da = tryParseNumber(sa);
          Double db = tryParseNumber(sb);

          boolean na = da != null;
          boolean nb = db != null;

          if (na && nb) return Double.compare(da, db);
          if (na != nb) return na ? -1 : 1; // numbers first
          return collator.compare(sa, sb);
        };
    }
  }

  private static Comparator<ZPEType> valueComparatorWithMode(Mode mode, boolean descending) {
    Comparator<ZPEType> c = valueComparator(mode);
    return descending ? c.reversed() : c;
  }

  private static ZPEList copyList(ZPEList in) {
    ZPEList out = new ZPEList();
    // ZPEList likely is iterable; fall back to index approach
    try {
      for (int i = 0; i < in.size(); i++) {
        out.add(in.get(i));
      }
    } catch (Exception e) {
      // If ZPEList doesn't support size/get, this will need adapting to your actual API
      throw new ZPERuntimeException("sort: incompatible list implementation.");
    }
    return out;
  }

  // ---------------------- Functions ----------------------

  public static class Sort implements ZPECustomFunction {

    @Override public String getManualEntry() { return "Sorts a list and returns a new sorted list."; }
    @Override public String getManualHeader() { return "sort"; }

    @Override public int getMinimumParameters() { return 1; }

    @Override
    public String[] getParameterNames() {
      return new String[]{"items", "mode", "descending"};
    }

    @Override
    public ZPEType MainMethod(HashMap<String, Object> params, ZPERuntimeEnvironment runtime, ZPEFunction zpeFunction) {
      try {
        Object itemsObj = params.get("items");
        if (!(itemsObj instanceof ZPEList)) return new ZPEBoolean(false);

        ZPEList items = (ZPEList) itemsObj;

        Mode mode = parseMode(params.get("mode"));
        boolean descending = parseDescending(params.get("descending"));

        ZPEList out = copyList(items);

        // stable sort
        List<ZPEType> tmp = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) tmp.add(out.get(i));

        tmp.sort(valueComparatorWithMode(mode, descending));

        ZPEList result = new ZPEList();
        for (ZPEType t : tmp) result.add(t);

        return result;
      } catch (Exception e) {
        return new ZPEBoolean(false);
      }
    }

    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public byte getReturnType() { return YASSByteCodes.MIXED_TYPE; }
  }

  public static class SortBy implements ZPECustomFunction {

    @Override public String getManualEntry() { return "Sorts a list of maps by a key and returns a new sorted list."; }
    @Override public String getManualHeader() { return "sort_by"; }

    @Override public int getMinimumParameters() { return 2; }

    @Override
    public String[] getParameterNames() {
      return new String[]{"items", "key", "mode", "descending"};
    }

    @Override
    public ZPEType MainMethod(HashMap<String, Object> params, ZPERuntimeEnvironment runtime, ZPEFunction zpeFunction) {
      try {
        Object itemsObj = params.get("items");
        if (!(itemsObj instanceof ZPEList)) return new ZPEBoolean(false);

        String key = String.valueOf(params.get("key"));

        Mode mode = parseMode(params.get("mode"));
        boolean descending = parseDescending(params.get("descending"));

        ZPEList items = (ZPEList) itemsObj;

        // Build stable sort list
        List<ZPEType> tmp = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) tmp.add(items.get(i));

        Comparator<ZPEType> vc = valueComparator(mode);

        Comparator<ZPEType> cmp = (a, b) -> {
          ZPEType va = extractKey(a, key);
          ZPEType vb = extractKey(b, key);

          // Missing keys go last
          if (va == null && vb == null) return 0;
          if (va == null) return 1;
          if (vb == null) return -1;

          return vc.compare(va, vb);
        };

        if (descending) cmp = cmp.reversed();

        tmp.sort(cmp);

        ZPEList result = new ZPEList();
        for (ZPEType t : tmp) result.add(t);

        return result;

      } catch (Exception e) {
        return new ZPEBoolean(false);
      }
    }

    private ZPEType extractKey(ZPEType item, String key) {
      if (!(item instanceof ZPEMap)) return null;
      ZPEMap m = (ZPEMap) item;

      // Your ZPEMap likely uses ZPEString keys; keep it flexible:
      try {
        return (ZPEType) m.get(key); // if map supports String key directly
      } catch (Exception ignored) {
      }

      try {
        return (ZPEType) m.get(jamiebalfour.zpe.types.ZPEString.newStr(key));
      } catch (Exception ignored) {
      }

      return null;
    }

    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public byte getReturnType() { return YASSByteCodes.MIXED_TYPE; }
  }

  public static class SortMapKeys implements ZPECustomFunction {

    @Override public String getManualEntry() { return "Returns the keys of a map in sorted order."; }
    @Override public String getManualHeader() { return "sort_map_keys"; }

    @Override public int getMinimumParameters() { return 1; }

    @Override
    public String[] getParameterNames() {
      return new String[]{"map", "mode", "descending"};
    }

    @Override
    public ZPEType MainMethod(HashMap<String, Object> params, ZPERuntimeEnvironment runtime, ZPEFunction zpeFunction) {
      try {
        Object mObj = params.get("map");
        if (!(mObj instanceof ZPEMap)) return new ZPEBoolean(false);

        ZPEMap m = (ZPEMap) mObj;

        Mode mode = parseMode(params.get("mode"));
        boolean descending = parseDescending(params.get("descending"));

        List<ZPEType> keys = new ArrayList<>();
        for (Object k : m.keySet()) {
          keys.add((ZPEType) k);
        }

        keys.sort(valueComparatorWithMode(mode, descending));

        ZPEList out = new ZPEList();
        for (ZPEType k : keys) out.add(k);
        return out;
      } catch (Exception e) {
        return new ZPEBoolean(false);
      }
    }

    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public byte getReturnType() { return YASSByteCodes.MIXED_TYPE; }
  }

  public static class SortMapValues implements ZPECustomFunction {

    @Override public String getManualEntry() { return "Returns the values of a map in sorted order."; }
    @Override public String getManualHeader() { return "sort_map_values"; }

    @Override public int getMinimumParameters() { return 1; }

    @Override
    public String[] getParameterNames() {
      return new String[]{"map", "mode", "descending"};
    }

    @Override
    public ZPEType MainMethod(HashMap<String, Object> params, ZPERuntimeEnvironment runtime, ZPEFunction zpeFunction) {
      try {
        Object mObj = params.get("map");
        if (!(mObj instanceof ZPEMap)) return new ZPEBoolean(false);

        ZPEMap m = (ZPEMap) mObj;

        Mode mode = parseMode(params.get("mode"));
        boolean descending = parseDescending(params.get("descending"));

        List<ZPEType> vals = new ArrayList<>();
        for (Object k : m.keySet()) {
          vals.add((ZPEType) m.get((ZPEType) k));
        }

        vals.sort(valueComparatorWithMode(mode, descending));

        ZPEList out = new ZPEList();
        for (ZPEType v : vals) out.add(v);
        return out;
      } catch (Exception e) {
        return new ZPEBoolean(false);
      }
    }

    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public byte getReturnType() { return YASSByteCodes.MIXED_TYPE; }
  }
}