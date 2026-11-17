package com.horizonglitch.wotv.exceptions;

public class InsufficientShardsException extends RuntimeException {
    private final int shardBalance;
    private final int shardCost;

    public InsufficientShardsException(int shardBalance, int shardCost) {
        super("Not enough Shards to open this pack");
        this.shardBalance = shardBalance;
        this.shardCost = shardCost;
    }

    public int getShardBalance() { return shardBalance; }
    public int getShardCost() { return shardCost; }
}
