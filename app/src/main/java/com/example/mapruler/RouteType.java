package com.example.mapruler;

public enum RouteType {
    TEST_ROUTE("testRouteDeleteFromDB"),
    CURR_LOC_START_SINGLE("currentLocationStartSingle"),
    GIVEN_LOC_START_SINGLE("givenLocationStartSingle"),
    CURR_LOC_START_MULTI("currentLocationStartMulti"),
    GIVEN_LOC_START_MULTI("givenLocationStartMulti");

    private final String parsableLabel;

    public String getParsableLabel() {
        return this.parsableLabel;
    }

    public static RouteType valueOfLabel(String parsableLabel){
        for (RouteType r : values()) {
            if (r.getParsableLabel().equals(parsableLabel)) {
                return r;
            }
        }
        return null;
    }

    RouteType(String parsableLabel){
        this.parsableLabel = parsableLabel;
    }
}
