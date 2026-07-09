package com.xrq.xxq.module.selection.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.xrq.xxq.module.selection.dto.SelectionClassResponse;
import com.xrq.xxq.module.selection.service.SelectionClassService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionClassServiceImpl implements SelectionClassService {

    @Override
    public void finalize(Long campaignId) {
        // Task 9 实现
    }

    @Override
    public List<SelectionClassResponse> listByCampaign(Long campaignId) {
        return List.of();
    }
}
