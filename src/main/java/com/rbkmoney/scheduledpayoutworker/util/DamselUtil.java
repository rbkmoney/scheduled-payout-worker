package com.rbkmoney.scheduledpayoutworker.util;

import com.rbkmoney.damsel.domain.*;
import com.rbkmoney.damsel.payment_processing.ClaimStatus;
import com.rbkmoney.damsel.payment_processing.PartyChange;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DamselUtil {

    public static Long computeMerchantAmount(List<FinalCashFlowPosting> finalCashFlow) {
        long amountSource = computeAmount(finalCashFlow, FinalCashFlowPosting::getSource);
        long amountDest = computeAmount(finalCashFlow, FinalCashFlowPosting::getDestination);
        return amountDest - amountSource;
    }

    private static long computeAmount(List<FinalCashFlowPosting> finalCashFlow,
                                      Function<FinalCashFlowPosting, FinalCashFlowAccount> func) {
        return finalCashFlow.stream()
                .filter(f -> isMerchantSettlement(func.apply(f).getAccountType()))
                .mapToLong(cashFlow -> cashFlow.getVolume().getAmount())
                .sum();
    }

    private static boolean isMerchantSettlement(CashFlowAccount cashFlowAccount) {
        return cashFlowAccount.isSetMerchant()
                && cashFlowAccount.getMerchant() == MerchantCashFlowAccount.settlement;
    }

    public static Map<CashFlowType, Long> parseCashFlow(List<FinalCashFlowPosting> finalCashFlow) {
        return finalCashFlow.stream().collect(
                Collectors.groupingBy(CashFlowType::getCashFlowType,
                        Collectors.summingLong(cashFlow -> cashFlow.getVolume().getAmount())));
    }

    private DamselUtil() {
        throw new UnsupportedOperationException("Unable to instantiate utility class!");
    }

    public static boolean hasPaymentInstitutionAccountPayTool(Party party,
                                                              String shopContractId,
                                                              String shopPayoutToolId) {
        Optional<Contract> contractOptional = party.getContracts().values().stream()
                .filter(contract -> contract.getId().equals(shopContractId))
                .filter(contract -> contract.getPayoutTools().stream()
                        .anyMatch(payoutToolValue -> payoutToolValue.getId().equals(shopPayoutToolId)))
                .findFirst();

        if (contractOptional.isEmpty()) {
            return false;
        }

        Optional<PayoutTool> payoutToolOptional = contractOptional.get().getPayoutTools().stream()
                .filter(
                        payoutTool -> payoutTool.getPayoutToolInfo().isSetPaymentInstitutionAccount()
                )
                .findFirst();

        return payoutToolOptional.isPresent();
    }

    public static ClaimStatus getClaimStatus(PartyChange change) {
        ClaimStatus claimStatus = null;
        if (change.isSetClaimCreated()) {
            claimStatus = change.getClaimCreated().getStatus();
        } else if (change.isSetClaimStatusChanged()) {
            claimStatus = change.getClaimStatusChanged().getStatus();
        }
        return claimStatus;
    }

    public static boolean isClaimAccepted(PartyChange change) {
        ClaimStatus claimStatus = getClaimStatus(change);
        return claimStatus != null && claimStatus.isSetAccepted();
    }

}
