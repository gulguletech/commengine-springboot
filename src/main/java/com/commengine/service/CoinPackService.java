package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.CreateCoinPackRequest;
import com.commengine.dto.UpdateCoinPackRequest;
import com.commengine.entity.CoinPack;
import com.commengine.repository.CoinPackRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CoinPackService {

    private final CoinPackRepository repo;

    public CoinPackService(CoinPackRepository repo) {
        this.repo = repo;
    }

    /** What the app shows: active packs only. */
    public List<Map<String, Object>> publicList() {
        return repo.findByActiveTrueOrderBySortOrderAscPriceInrAscCreatedAtAsc().stream().map(p -> toMap(p, false)).toList();
    }

    /** A pack the user is allowed to buy right now (exists and is active). */
    public CoinPack requireBuyable(String id) {
        return repo.findByIdAndActiveTrue(id).orElseThrow(() -> ApiException.badRequest("This package is no longer available"));
    }

    public List<Map<String, Object>> adminList() {
        return repo.findAllByOrderBySortOrderAscPriceInrAscCreatedAtAsc().stream().map(p -> toMap(p, true)).toList();
    }

    public Map<String, Object> adminCreate(CreateCoinPackRequest r) {
        CoinPack p = new CoinPack();
        p.setCoins(r.getCoins());
        p.setPriceInr(r.getPriceInr());
        if (r.getSortOrder() != null) p.setSortOrder(r.getSortOrder());
        if (r.getActive() != null) p.setActive(r.getActive());
        return toMap(repo.save(p), true);
    }

    public Map<String, Object> adminUpdate(String id, UpdateCoinPackRequest r) {
        CoinPack p = repo.findById(id).orElseThrow(() -> ApiException.notFound("Coin pack not found"));
        if (r.getCoins() != null) p.setCoins(r.getCoins());
        if (r.getPriceInr() != null) p.setPriceInr(r.getPriceInr());
        if (r.getSortOrder() != null) p.setSortOrder(r.getSortOrder());
        if (r.getActive() != null) p.setActive(r.getActive());
        p.setUpdatedAt(Instant.now());
        return toMap(repo.save(p), true);
    }

    /**
     * Hard delete. Safe for past purchases: every payment order stores its own coins + price snapshot,
     * and an order that is already in flight still credits the coins it was created with.
     */
    public void adminDelete(String id) {
        if (!repo.existsById(id)) throw ApiException.notFound("Coin pack not found");
        repo.deleteById(id);
    }

    private static Map<String, Object> toMap(CoinPack p, boolean admin) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("coins", p.getCoins());
        m.put("priceInr", p.getPriceInr());
        if (admin) {
            m.put("active", p.isActive());
            m.put("sortOrder", p.getSortOrder());
            m.put("createdAt", p.getCreatedAt().toString());
            m.put("updatedAt", p.getUpdatedAt().toString());
        }
        return m;
    }
}
