package iuh.fit.fashionwebsite.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BehaviorType {

    VIEW(1),
    ADD_TO_CART(5),
    PURCHASE(10),
    REMOVE_FROM_CART(-2)
    ;

    private final int weight;
}
