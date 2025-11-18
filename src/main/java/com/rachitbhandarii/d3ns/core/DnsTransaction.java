package com.rachitbhandarii.d3ns.core;

import java.util.Objects;

// record for storing the dns transactions to be stored on the blockchain
public record DnsTransaction(String domain, String ip) {

    @Override
    public String toString() {
        return domain + ":" + ip;
    }

    // uniqueness of a transaction ->

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        DnsTransaction that = (DnsTransaction) other;
        return domain.equalsIgnoreCase(that.domain) &&
                Objects.equals(ip, that.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain.toLowerCase(), ip);
    }

}
