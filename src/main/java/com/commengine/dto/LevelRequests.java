package com.commengine.dto;

/** Body for the admin "set a level" call. */
public class LevelRequests {

    public static class SetLevel {
        /** Gift value (coins) a user must have received to reach this level. */
        private Long requiredCoins;
        /** NONE, COINS or ITEM */
        private String type;
        private Long coins;
        private String itemId;
        private Integer itemDays;

        public Long getRequiredCoins() { return requiredCoins; }
        public void setRequiredCoins(Long requiredCoins) { this.requiredCoins = requiredCoins; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Long getCoins() { return coins; }
        public void setCoins(Long coins) { this.coins = coins; }
        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }
        public Integer getItemDays() { return itemDays; }
        public void setItemDays(Integer itemDays) { this.itemDays = itemDays; }
    }
}
