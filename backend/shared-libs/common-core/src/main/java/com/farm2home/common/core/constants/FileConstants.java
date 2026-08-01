package com.farm2home.common.core.constants;

/** Upload category names, passed to common-web's FileStorageService.store(file, category).
 *  Size limits and allowed content types are deliberately NOT duplicated here - they're
 *  already centralized as configurable defaults on FileStorageProperties
 *  (app.upload.* in common-web), which stays the single source of truth for those. */
public final class FileConstants {

    private FileConstants() {
    }

    public static final String CATEGORY_CUSTOMERS = "customers";
    public static final String CATEGORY_FARMS = "farms";
    public static final String CATEGORY_PRODUCTS = "products";
}
