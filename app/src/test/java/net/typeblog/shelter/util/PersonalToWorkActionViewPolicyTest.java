package net.typeblog.shelter.util;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersonalToWorkActionViewPolicyTest {
    private static final int PERSONAL_TO_WORK_FLAG =
            DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;

    private static final List<String> EXPECTED_SCHEME_COVERAGE = Arrays.asList(
            "content", "file", "ftp", "ftps", "mailto", "geo", "market",
            "tel", "sms", "smsto", "mms", "mmsto", "intent", "package");

    private static final List<String> COMMON_LINK_SCHEMES = Arrays.asList(
            "mailto", "geo", "market", "tel", "sms", "smsto", "mms", "mmsto");

    @Test
    public void personalToWorkActionViewSpecs_commonLinkSchemesIncludeBrowsableAndDefault() {
        List<PersonalToWorkActionViewPolicy.FilterSpec> commonLinkSpecs =
                PersonalToWorkActionViewPolicy.getFilterSpecs().stream()
                        .filter(spec -> spec.schemes.stream().anyMatch(COMMON_LINK_SCHEMES::contains))
                        .collect(Collectors.toList());

        assertEquals(2, commonLinkSpecs.size());

        assertTrue(commonLinkSpecs.stream().anyMatch(spec ->
                spec.categories.contains(Intent.CATEGORY_BROWSABLE)
                        && spec.categories.contains(Intent.CATEGORY_DEFAULT)));
        assertTrue(commonLinkSpecs.stream().anyMatch(spec ->
                spec.categories.equals(Collections.singletonList(Intent.CATEGORY_BROWSABLE))));

        for (String scheme : COMMON_LINK_SCHEMES) {
            assertTrue(commonLinkSpecs.stream().anyMatch(spec -> spec.schemes.contains(scheme)));
        }
    }

    @Test
    public void personalToWorkActionViewSpecs_useManagedCanAccessParentDirection() {
        for (PersonalToWorkActionViewPolicy.FilterSpec spec
                : PersonalToWorkActionViewPolicy.getFilterSpecs()) {
            assertEquals(PERSONAL_TO_WORK_FLAG, spec.directionFlag);
        }
    }

    @Test
    public void personalToWorkActionViewSpecs_allUseActionView() {
        for (PersonalToWorkActionViewPolicy.FilterSpec spec
                : PersonalToWorkActionViewPolicy.getFilterSpecs()) {
            assertEquals(Intent.ACTION_VIEW, spec.action);
        }
    }

    @Test
    public void personalToWorkActionViewSpecs_includeDistinctMimeWildcardFilter() {
        List<PersonalToWorkActionViewPolicy.FilterSpec> mimeSpecs =
                PersonalToWorkActionViewPolicy.getFilterSpecs().stream()
                        .filter(spec -> spec.mimeTypes.contains("*/*"))
                        .collect(Collectors.toList());

        assertEquals(1, mimeSpecs.size());

        PersonalToWorkActionViewPolicy.FilterSpec mimeSpec = mimeSpecs.get(0);
        assertTrue(mimeSpec.categories.contains(Intent.CATEGORY_DEFAULT));
        assertTrue(mimeSpec.schemes.isEmpty());
    }

    @Test
    public void personalToWorkActionViewSpecs_mirrorHttpHttpsBrowserFilters() {
        List<PersonalToWorkActionViewPolicy.FilterSpec> httpSpecs =
                PersonalToWorkActionViewPolicy.getFilterSpecs().stream()
                        .filter(spec -> spec.schemes.contains("http")
                                && spec.schemes.contains("https"))
                        .collect(Collectors.toList());

        assertEquals(2, httpSpecs.size());

        PersonalToWorkActionViewPolicy.FilterSpec browsableOnly = httpSpecs.stream()
                .filter(spec -> spec.categories.equals(
                        Collections.singletonList(Intent.CATEGORY_BROWSABLE)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing BROWSABLE-only http/https spec"));
        PersonalToWorkActionViewPolicy.FilterSpec browsableDefault = httpSpecs.stream()
                .filter(spec -> spec.categories.contains(Intent.CATEGORY_BROWSABLE)
                        && spec.categories.contains(Intent.CATEGORY_DEFAULT))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing BROWSABLE+DEFAULT http/https spec"));

        assertEquals(Arrays.asList("http", "https"), browsableOnly.schemes);
        assertEquals(Arrays.asList("http", "https"), browsableDefault.schemes);
        assertTrue(browsableDefault.mimeTypes.isEmpty());
    }

    @Test
    public void personalToWorkActionViewSpecs_coverExplicitUriSchemes() {
        Set<String> coveredSchemes = new HashSet<>();
        for (PersonalToWorkActionViewPolicy.FilterSpec spec
                : PersonalToWorkActionViewPolicy.getFilterSpecs()) {
            coveredSchemes.addAll(spec.schemes);
        }

        for (String scheme : EXPECTED_SCHEME_COVERAGE) {
            assertTrue("missing scheme coverage for " + scheme, coveredSchemes.contains(scheme));
        }

        assertFalse("must not use wildcard scheme", coveredSchemes.contains("*"));
    }

    @Test
    public void personalToWorkActionViewSpecs_doNotUseParentCanAccessManaged() {
        for (PersonalToWorkActionViewPolicy.FilterSpec spec
                : PersonalToWorkActionViewPolicy.getFilterSpecs()) {
            assertFalse(DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED == spec.directionFlag);
        }
    }
}
