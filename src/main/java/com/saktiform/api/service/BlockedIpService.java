package com.saktiform.api.service;

import com.saktiform.api.entity.BlockedIp;
import com.saktiform.api.repository.BlockedIpRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Service
public class BlockedIpService {
    private final BlockedIpRepository blockedIpRepository;

    private static final long CACHE_TTL_MS = 60_000;
    private volatile Set<String> cache = new HashSet<>();
    private volatile long lastLoad = 0;

    public BlockedIpService(BlockedIpRepository blockedIpRepository) {
        this.blockedIpRepository = blockedIpRepository;
    }

    public boolean isBlocked(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return getCache().contains(ip);
    }

    private Set<String> getCache() {
        if (System.currentTimeMillis() - lastLoad > CACHE_TTL_MS) {
            refreshCache();
        }
        return cache;
    }

    private synchronized void refreshCache() {
        cache = new HashSet<>(blockedIpRepository.findAllIpAddresses());
        lastLoad = System.currentTimeMillis();
    }

    public BlockedIp create(String ipAddress, String requesterIp) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("IP address tidak boleh kosong");
        }
        ipAddress = ipAddress.trim();
        if (isProtectedIp(ipAddress, requesterIp)) {
            throw new IllegalArgumentException("IP ini tidak boleh diblokir");
        }
        if (blockedIpRepository.existsByIpAddress(ipAddress)) {
            throw new IllegalArgumentException("IP ini sudah diblokir");
        }

        BlockedIp blockedIp = new BlockedIp();
        blockedIp.setIpAddress(ipAddress);
        blockedIp.setCreatedAt(Instant.now());
        blockedIp.setUpdatedAt(Instant.now());
        BlockedIp saved = blockedIpRepository.save(blockedIp);
        refreshCache();
        return saved;
    }

    public Page<BlockedIp> list(Integer page, Integer limit) {
        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return blockedIpRepository.findAll(pageable);
    }

    public void delete(Long id) {
        blockedIpRepository.deleteById(id);
        refreshCache();
    }

    private boolean isProtectedIp(String ip, String requesterIp) {
        if (ip.equals(requesterIp)) {
            return true;
        }
        if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0.0.0.0")) {
            return true;
        }
        try {
            if (ip.equals(InetAddress.getLocalHost().getHostAddress())) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
