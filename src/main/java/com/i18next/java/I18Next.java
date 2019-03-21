/**
 *
 */
package com.i18next.java;

import java.io.StringReader;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;

/**
 * @author stan
 */
public class I18Next {

    /**
     * Used locally to tag Logs
     */
    //private static final String TAG = I18Next.class.getSimpleName();

    private static final Logger LOG = Logger.getLogger(I18Next.class.getName());

    private static final String PREF_KEY_I18N = "i18n_json";

    private static final String SEPARATOR_LANGUAGE_COUNTRY = "_";
    private static final String WRONG_SEPARATOR_LANGUAGE_COUNTRY = "_";

    private static final String COMMA = ",";

    private Options mOptions = new Options();
    private JsonObject mRootObject = Json.createObjectBuilder().build();

    /**
     * SingletonHolder is loaded on the first execution of
     * Singleton.getInstance() or the first access to SingletonHolder.INSTANCE,
     * not before.
     */
    private static class SingletonHolder {

        public static final I18Next INSTANCE = new I18Next();
    }

    public I18Next() {
    }

    public static I18Next getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public Loader loader() {
        return new Loader(this);
    }

    enum LogMode {
        VERBOSE, WARNING, ERROR;
    }

//    public void saveInPreference(SharedPreferences sharedPreference) {
//        sharedPreference.edit().putString(PREF_KEY_I18N, mRootObject.toString()).commit();
//    }
//
//    public void loadFromPreference(SharedPreferences sharedPreference) {
//        String content = sharedPreference.getString(PREF_KEY_I18N, null);
//        if (content != null && content.length() > 0) {
//            try {
//                mRootObject = new JSONObject(content);
//            } catch (JSONException e) {
//                Log.w(TAG, e);
//            }
//        }
//    }
    public static boolean isI18NextKeyCandidate(CharSequence key) {
        if (key != null && key.length() > 0) {
            return key.toString().matches("([a-z0-9]+((\\_)([a-z0-9]+))*)+((\\.)[a-z0-9]+((\\_)([a-z0-9]+))*)+");
        } else {
            return false;
        }
    }

    public boolean isEmpty() {
        return mRootObject.isEmpty();
    }

    public Options getOptions() {
        return mOptions;
    }

    void log(String raw, Object... args) {
        log(LogMode.VERBOSE, raw, args);
    }

    void log(LogMode logMode, String raw, Object... args) {
        if (mOptions.isDebugMode()) {
            if (args != null) {
                raw = String.format(raw, args);
            }
            switch (logMode) {
                case ERROR:
                    LOG.log(Level.SEVERE, raw);
                    break;
                case WARNING:
                    LOG.log(Level.WARNING, raw);
                    break;
                case VERBOSE:
                    LOG.log(Level.INFO, raw);
                    break;

            }
        }
    }

    private String[] splitKeyPath(String key) {
        if (key != null) {
            String[] splitKeys = key.split(Pattern.quote(mOptions.getKeySeparator()));
            if (splitKeys == null) {
                log(LogMode.ERROR, "impossible to split key '%s'", key);
            }
            return splitKeys;
        }
        return null;
    }

    private String getNamespace(String key) {
        if (key != null) {
            int indexOfNS = key.indexOf(mOptions.getNsSeparator());
            if (indexOfNS > 0) {
                String namespace = key.substring(0, indexOfNS);
                log("namespace found for key '%s': %s", key, namespace);
                return namespace;
            }
        }
        return mOptions.getDefaultNamespace();
    }

    private void initDefaultNamespaceIfNeeded(String nameSpace) {
        if (mOptions.getDefaultNamespace() == null) {
            log("namespace taken from the first key available (it's now our default namespace): %s", nameSpace);
            mOptions.setDefaultNamespace(nameSpace);
        }
    }

    public String _t(String lang, String key) {
        return _t(lang, key, null);
    }

    public String t(String key) {
        return _t(mOptions.getLanguage(), key);
    }

    /**
     * Using multiple keys (first found will be translated)
     *
     * @param keys
     * @return
     */
//    public String _t(String lang, String... keys) {
//        return t(lang, keys, null);
//    }
    public String t(String... keys) {
        return _t(mOptions.getLanguage(), keys, null);
    }

    public String _t(String lang, String key, Operation operation) {
        String[] keys = {key};
        return _t(lang, keys, operation);
    }

    public String t(String key, Operation operation) {
        return _t(mOptions.getLanguage(), key, operation);
    }

    public String _t(String lang, String[] keys, Operation operation) {
        String innerProcessValue = null;
        if (keys != null && keys.length > 0) {
            for (String key : keys) {
                String rawValue = getValueRaw(lang, key, operation);
                innerProcessValue = transformRawValue(lang, operation, rawValue);
                if (innerProcessValue != null) {
                    break;
                } else if (mOptions.isDebugMode()) {
                    log(LogMode.WARNING, "impossible to found key '%s'", key);
                }
            }
        }
        return innerProcessValue;
    }

    public String t(String[] keys, Operation operation) {
        return _t(mOptions.getLanguage(), keys, operation);
    }

    private String transformRawValue(String lang, Operation operation, String rawValue) {
        String innerProcessValue;
        if (operation instanceof Operation.PostOperation) {
            rawValue = ((Operation.PostOperation) operation).postProcess(lang, rawValue);
        }
        String rawValueNestingReplaced = getRawWithNestingReplaced(rawValue, operation);
        if (rawValueNestingReplaced != null) {
            innerProcessValue = transformRawValue(lang, operation, rawValueNestingReplaced);
        } else {
            innerProcessValue = rawValue;
        }
        return innerProcessValue;
    }

    public boolean existValue(String lang, String key) {
        return getValueRaw(lang, key, null) != null;
    }

    public boolean existValue(String key) {
        return existValue(mOptions.getLanguage(), key);
    }

    private String getValueRaw(String lang, String key, Operation operation) {
        if (key == null) {
            return null;
        }
        String value = null;
        String namespace = getNamespace(key);
        if (namespace != null) {
            if (key.startsWith(namespace)) {
                key = key.substring(namespace.length() + 1); // +1 for the colon
            }
            if (operation instanceof Operation.PreOperation) {
                // it's the last key part
                key = ((Operation.PreOperation) operation).preProcess(lang, key);
            }

            value = getValueRawWithoutPreprocessing(lang, namespace, key);

            if (value == null && operation instanceof Operation.PreOperation) {
                String repreProcessedKey = ((Operation.PreOperation) operation).preProcessAfterNoValueFound(key);
                if (repreProcessedKey != null && !repreProcessedKey.equals(key)) {
                    value = getValueRawWithoutPreprocessing(lang, namespace, repreProcessedKey);
                }
            }
        }
        return value;
    }

    private String getValueRawWithoutPreprocessing(String lang, String namespace, String key) {
        String value;

        String[] splitKeys = splitKeyPath(key);
        if (splitKeys == null) {
            value = null;
        } else {
            value = getValueRawByLanguageWithNamespace(lang, namespace, splitKeys);
            if (value == null) {
                value = getValueRawByLanguageWithNamespace(mOptions.getFallbackLanguage(), namespace, splitKeys);
            }
        }
        return value;
    }

    private String getValueRawByLanguageWithNamespace(String lang, String namespace, String[] splitKeys) {
        JsonObject rootObject = getRootObjectByLang(lang);
        if (rootObject != null) {
            Object o = rootObject.get(namespace);   //opt
            for (int i = 0; i < splitKeys.length; i++) {
                String splitKey = splitKeys[i];
                if (o instanceof JsonObject) {
                    o = ((JsonObject) o).get(splitKey); //opt
                } else {
                    o = null;
                    break;
                }
            }
            if (o instanceof JsonString) {
                return ((JsonString) o).getString();
            }
        }
        return null;
    }

    private JsonObject getRootObjectByLang(String lang) {
        JsonObject result = null;
        if (lang != null) {
            result = mRootObject.getJsonObject(lang);
            if (result == null) {
                int indexOfLangSeparator = lang.lastIndexOf(SEPARATOR_LANGUAGE_COUNTRY);
                if (indexOfLangSeparator > 0) {
                    // found a separator
                    result = getRootObjectByLang(lang.substring(0, indexOfLangSeparator));
                }
            }
        }
        return result;
    }

    public JsonObject getRawObject(String lang) {
        return getRootObjectByLang(lang);
    }

    public JsonObject getRawObject(String lang, String namespace) {
        JsonObject rootObject = getRootObjectByLang(lang);
        if (rootObject != null) {
            return rootObject.getJsonObject(namespace);
        }
        return null;
    }
    
    public Set<String> getAvailableLangs(){
        return mRootObject.keySet();
    }

    static String getConvertLang(String lang) {
        if (lang.contains(WRONG_SEPARATOR_LANGUAGE_COUNTRY)) {
            lang = lang.replaceAll(WRONG_SEPARATOR_LANGUAGE_COUNTRY, SEPARATOR_LANGUAGE_COUNTRY);
        }
        return lang;
    }

    private String getRawWithNestingReplaced(String raw, Operation operation) {
        // nesting
        String reusePrefix = mOptions.getReusePrefix();
        String reuseSuffix = mOptions.getReuseSuffix();
        if (raw != null && raw.length() > 0 && reusePrefix != null && reuseSuffix != null && reusePrefix.length() > 0 && reuseSuffix.length() > 0) {
            int indexOfPrefix = raw.indexOf(reusePrefix);
            if (indexOfPrefix >= 0) {
                int indexOfSuffix = raw.indexOf(reuseSuffix, indexOfPrefix);
                if (indexOfSuffix > 0) {
                    // we've found a prefix and a suffix
                    String param = raw.substring(indexOfPrefix, indexOfSuffix + reuseSuffix.length());
                    String paramTrim = param.substring(reusePrefix.length(), indexOfSuffix - indexOfPrefix);

                    // nested replacement
                    int commaIndex = paramTrim.indexOf(COMMA);
                    while (commaIndex > 0 && paramTrim.length() > commaIndex + 1) {
                        String textLeft = paramTrim.substring(commaIndex + 1);
                        try {

                            JsonReader jsonReader = Json.createReader(new StringReader(textLeft));
                            JsonObject jsonObject = jsonReader.readObject();
                            jsonReader.close();

                            JsonNumber countJsonParam = jsonObject.getJsonNumber("count");
                            String countParam = null;
                            if (countJsonParam != null) {
                                countParam = String.valueOf(countJsonParam.intValue());
                            }
                            if (!/*TextUtils.isEmpty(countParam)*/(countParam == null || countParam.isEmpty())) {
                                String countParamWithReplace
                                        = getRawWithNestingReplaced(countParam, operation);
                                if (countParamWithReplace != null) {
                                    countParam = countParamWithReplace;
                                }
                                paramTrim = paramTrim.substring(0, commaIndex);
                                try {
                                    Operation.Plural replacePlural;
                                    try {
                                        replacePlural
                                                = new Operation.Plural(Integer.parseInt(countParam));
                                    } catch (NumberFormatException ex) {
                                        replacePlural
                                                = new Operation.Plural(Float.parseFloat(countParam));
                                    }
                                    if (operation == null) {
                                        operation = replacePlural;
                                    } else {
                                        operation = new Operation.MultiPostProcessing(
                                                replacePlural, operation);
                                    }
                                    break;
                                } catch (NumberFormatException ex) {
                                }
                            }
                        } catch (JsonException e) {
                            commaIndex = paramTrim.indexOf(COMMA, commaIndex + 1);
                        }
                    }

                    String replacement = t(paramTrim, operation);
                    if (replacement == null) {
                        replacement = "";
                    }
                    int hashBefore = raw.hashCode();
                    raw = raw.replace(param, replacement);
                    if (hashBefore != raw.hashCode()) {
                        // the string has been changed, try to change it again
                        String rawWithSubReplacement = getRawWithNestingReplaced(raw, operation);
                        if (rawWithSubReplacement != null) {
                            raw = rawWithSubReplacement;
                        }
                    }
                    return raw;
                }
            }
        }
        return null;
    }

    private void load(String lang, String namespace, JsonObject json) throws JsonException {
        JsonObjectBuilder rootLanguageBuilder = Json.createObjectBuilder();
        JsonObject rootLanguage = mRootObject.getJsonObject(getConvertLang(lang));
        if (rootLanguage != null) {
            rootLanguage.forEach(rootLanguageBuilder::add);
        }
        rootLanguageBuilder.add(namespace, json);

        JsonObjectBuilder mRootObjectBuilder = Json.createObjectBuilder();
        mRootObject.forEach(mRootObjectBuilder::add);
        mRootObjectBuilder.add(lang, rootLanguageBuilder.build());

        mRootObject = mRootObjectBuilder.build();

        initDefaultNamespaceIfNeeded(namespace);
    }

    static boolean equalsCharSequence(CharSequence cs, CharSequence cs2) {
        return (cs2 == null && cs == null) || (cs != null && cs.equals(cs2));
    }

    public static class Loader {

        private JsonObject mJSONObject;
        private String mNameSpace;
        private String mLang;
        private I18Next mI18Next;

        public Loader(I18Next i18Next) {
            mI18Next = i18Next;
        }

//        public Loader from(Context context, int resource) throws JSONException, IOException {
//            String json = null;
//            InputStream inputStream;
//            try {
//                inputStream = context.getResources().openRawResource(resource);
//            } catch (Exception ex) {
//                try {
//                    json = context.getResources().getString(resource);
//                } catch (Exception ex2) {
//                }
//                inputStream = null;
//            }
//            if (json == null && inputStream != null) {
//                InputStreamReader is = new InputStreamReader(inputStream);
//                StringBuilder sb = new StringBuilder();
//                BufferedReader br = new BufferedReader(is);
//                String read = br.readLine();
//                while (read != null) {
//                    sb.append(read);
//                    read = br.readLine();
//                }
//                json = sb.toString();
//            }
//            return from(json);
//        }
        public Loader from(String json) throws JsonException {
            JsonReader jsonReader = Json.createReader(new StringReader(json));
            JsonObject object = jsonReader.readObject();
            jsonReader.close();

            return from(object);
        }

        public Loader from(JsonObject jsonObject) {
            mJSONObject = jsonObject;
            return this;
        }

        public Loader namespace(String nameSpace) {
            mNameSpace = nameSpace;
            return this;
        }

        public Loader lang(String lang) {
            mLang = lang;
            return this;
        }

        public void load() throws JsonException {
            if (mLang == null) {
                mLang = mI18Next.mOptions.getLanguage();
            }
            if (mNameSpace == null) {
                mNameSpace = mI18Next.mOptions.getDefaultNamespace();
                if (mNameSpace == null) {
                    mNameSpace = "DEFAULT_NAMESPACE";
                }
            }
            mI18Next.load(mLang, mNameSpace, mJSONObject);
        }
    }
}
