package net.typeblog.shelter.util;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PersonalToWorkActionViewPolicy {
    public static final int DIRECTION_FLAG =
            DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;

    private static final List<String> HTTP_SCHEMES =
            Collections.unmodifiableList(Arrays.asList("http", "https"));
    private static final List<String> FTP_SCHEMES =
            Collections.unmodifiableList(Arrays.asList("ftp", "ftps"));
    private static final List<String> CONTENT_SCHEMES =
            Collections.unmodifiableList(Arrays.asList(
                    "content", "file", "intent", "package"));
    private static final List<String> COMMON_LINK_SCHEMES =
            Collections.unmodifiableList(Arrays.asList(
                    "mailto", "geo", "market", "tel", "sms", "smsto", "mms", "mmsto"));

    public static final class FilterSpec {
        public final String action;
        public final List<String> categories;
        public final List<String> schemes;
        public final List<String> mimeTypes;
        public final int directionFlag;

        private FilterSpec(String action, List<String> categories,
                           List<String> schemes, List<String> mimeTypes) {
            this.action = action;
            this.categories = Collections.unmodifiableList(categories);
            this.schemes = Collections.unmodifiableList(schemes);
            this.mimeTypes = Collections.unmodifiableList(mimeTypes);
            this.directionFlag = DIRECTION_FLAG;
        }
    }

    private PersonalToWorkActionViewPolicy() {
    }

    public static List<FilterSpec> getFilterSpecs() {
        List<FilterSpec> specs = new ArrayList<>();
        specs.add(viewFilter(
                Collections.singletonList(Intent.CATEGORY_DEFAULT),
                Collections.emptyList(),
                Collections.singletonList("*/*")));
        specs.add(viewFilter(
                Collections.singletonList(Intent.CATEGORY_BROWSABLE),
                HTTP_SCHEMES,
                Collections.emptyList()));
        specs.add(viewFilter(
                Arrays.asList(Intent.CATEGORY_BROWSABLE, Intent.CATEGORY_DEFAULT),
                HTTP_SCHEMES,
                Collections.emptyList()));
        specs.add(viewFilter(
                Collections.singletonList(Intent.CATEGORY_DEFAULT),
                CONTENT_SCHEMES,
                Collections.emptyList()));
        specs.add(viewFilter(
                Collections.singletonList(Intent.CATEGORY_BROWSABLE),
                COMMON_LINK_SCHEMES,
                Collections.emptyList()));
        specs.add(viewFilter(
                Arrays.asList(Intent.CATEGORY_BROWSABLE, Intent.CATEGORY_DEFAULT),
                COMMON_LINK_SCHEMES,
                Collections.emptyList()));
        specs.add(viewFilter(
                Collections.singletonList(Intent.CATEGORY_BROWSABLE),
                FTP_SCHEMES,
                Collections.emptyList()));
        specs.add(viewFilter(
                Arrays.asList(Intent.CATEGORY_BROWSABLE, Intent.CATEGORY_DEFAULT),
                FTP_SCHEMES,
                Collections.emptyList()));
        return Collections.unmodifiableList(specs);
    }

    private static FilterSpec viewFilter(List<String> categories,
                                         List<String> schemes,
                                         List<String> mimeTypes) {
        return new FilterSpec(Intent.ACTION_VIEW, categories, schemes, mimeTypes);
    }

    public static IntentFilter buildIntentFilter(FilterSpec spec) {
        IntentFilter filter = new IntentFilter(spec.action);
        for (String category : spec.categories) {
            filter.addCategory(category);
        }
        for (String scheme : spec.schemes) {
            filter.addDataScheme(scheme);
        }
        for (String mimeType : spec.mimeTypes) {
            try {
                filter.addDataType(mimeType);
            } catch (IntentFilter.MalformedMimeTypeException ignored) {
                // WTF?
            }
        }
        return filter;
    }

    public static void registerFilters(DevicePolicyManager manager,
                                       android.content.ComponentName adminComponent) {
        for (FilterSpec spec : getFilterSpecs()) {
            manager.addCrossProfileIntentFilter(
                    adminComponent,
                    buildIntentFilter(spec),
                    spec.directionFlag);
        }
    }
}
