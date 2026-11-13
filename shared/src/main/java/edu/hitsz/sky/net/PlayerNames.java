package edu.hitsz.sky.net;

final class PlayerNames {
    private PlayerNames() { }

    static String normalize(String value) {
        String name = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) name = "飞行员";
        return name.length() > 16 ? name.substring(0, 16) : name;
    }
}
