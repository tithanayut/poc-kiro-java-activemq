package com.orderprocessing.producer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

public class OrderRequest {

    @NotNull(message = "orderId is required")
    @NotEmpty(message = "orderId is required")
    @JsonProperty("orderId")
    private String orderId;

    @NotNull(message = "productIds is required")
    @NotEmpty(message = "productIds cannot be empty")
    @JsonProperty("productIds")
    private List<String> productIds;

    public OrderRequest() {
    }

    public OrderRequest(String orderId, List<String> productIds) {
        this.orderId = orderId;
        this.productIds = productIds;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public List<String> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<String> productIds) {
        this.productIds = productIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderRequest that = (OrderRequest) o;
        return Objects.equals(orderId, that.orderId) && 
               Objects.equals(productIds, that.productIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, productIds);
    }

    @Override
    public String toString() {
        return "OrderRequest{" +
                "orderId='" + orderId + '\'' +
                ", productIds=" + productIds +
                '}';
    }
}
