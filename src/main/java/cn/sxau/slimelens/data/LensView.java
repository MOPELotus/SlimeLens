package cn.sxau.slimelens.data;

public record LensView(Kind kind, String subject, int page) {

    public enum Kind {
        BROWSER,
        CATEGORY_LIST,
        CATEGORY_ITEMS,
        MACHINE_LIST,
        SEARCH,
        ITEM,
        RECIPES,
        USES,
        MACHINE_RECIPES,
        RECIPE_DETAIL
    }

    public LensView withPage(int newPage) {
        return new LensView(kind, subject, Math.max(0, newPage));
    }

    public static LensView browser() {
        return new LensView(Kind.BROWSER, "", 0);
    }
}
