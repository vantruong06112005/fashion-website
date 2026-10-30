package iuh.fit.fashionwebsite.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BehaviorType {

    VIEW(1),
    DWELL_15S(2),
    SELECT_VARIANT(3),
    ADD_TO_CART(5),
    PURCHASE(10),
    REMOVE_FROM_CART(-2)
    ;

    private final int weight;
}
