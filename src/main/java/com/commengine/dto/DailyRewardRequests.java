package com.commengine.dto;

/** Body for the admin "set a day of the 7-day reward" call. */
public class DailyRewardRequests {

    public static class SetDay {
        /** NONE, COINS or ITEM */
        private String type;
        private Long coins;
        private String itemId;
        private Integer itemDays;

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
