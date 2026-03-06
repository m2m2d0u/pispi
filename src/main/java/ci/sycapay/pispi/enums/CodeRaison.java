package ci.sycapay.pispi.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CodeRaison {
    AB03("Timeout"),
    AB04("Account closed"),
    AB08("Transaction amount limit exceeded"),
    AB09("Insufficient guarantee"),
    AC03("Invalid creditor account number"),
    AC04("Closed account"),
    AC06("Account blocked"),
    AC07("Creditor account closed"),
    AG01("Transaction forbidden"),
    AG08("Operation not allowed on this channel"),
    AG10("Agent suspended"),
    AG11("Intermediary agent suspended"),
    AM02("Amount not allowed"),
    AM04("Insufficient funds"),
    AM09("Amount wrong"),
    AM14("Amount exceeds agreed limit"),
    AM21("Amount limit per transaction exceeded"),
    BE01("Inconsistent with end customer"),
    BE05("Unrecognized party"),
    BE17("Invalid creditor identification"),
    CH17("Payment scheme not found"),
    DS0A("Order cancelled"),
    DS0B("Order rejected by payer"),
    DS0C("Order rejected by system"),
    DS0D("Order rejected - timeout"),
    DS0E("Order rejected - duplicate"),
    DS0F("Order rejected - invalid format"),
    DS0H("Order rejected - other reason"),
    DS04("Order rejected by payer financial institution"),
    DT02("Invalid date"),
    DU04("Duplicate message ID"),
    FR01("Fraud suspected"),
    NARR("Narrative"),
    RC04("Insufficient processing capacity"),
    RR04("Regulatory reason"),
    RR07("Missing regulatory info"),
    AEXR("Already expired RTP"),
    AG03("Transaction type not supported"),
    ALAC("Already accepted RTP"),
    APAR("Already partially accepted RTP"),
    ARFR("Already refused RTP"),
    ARJR("Already rejected RTP"),
    IRNR("Initial RTP never received");

    private final String description;
}
