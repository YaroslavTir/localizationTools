package com.yaroslavtir.localization.tools;

public class Application {

    public static void main(String[] args) throws Exception {

        String dir = args[0];
        String regex = args[1];
        LocalizationTools localizationTools;
        if (args.length == 2) {
            localizationTools = new LocalizationTools(dir, regex, false, null, null);
        } else if (args.length == 3) {
            boolean keyAsValue = Boolean.parseBoolean(args[2]);
            localizationTools = new LocalizationTools(dir, regex, keyAsValue, null, null);
        } else if (args.length == 4) {
            String language = args[2];
            String yandexKey = args[3];

            localizationTools = new LocalizationTools(dir, regex, false, language, yandexKey);
        } else {
            throw new RuntimeException("wrong amount of arguments");
        }
        localizationTools.generateI18nJson();

    }

}
