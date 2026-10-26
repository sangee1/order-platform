package com.example.platform.order.api;

import com.example.platform.order.domain.OrderLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotEmpty List<@Valid Line> items) {

    public record Line(
            @NotBlank String sku,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0.01") BigDecimal unitPrice) {}

    public List<OrderLine> toLines() {
        return items.stream().map(l -> new OrderLine(l.sku(), l.quantity(), l.unitPrice())).toList();
    }
}
