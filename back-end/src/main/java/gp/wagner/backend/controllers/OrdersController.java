package gp.wagner.backend.controllers;

import gp.wagner.backend.domain.dto.request.crud.OrderRequestDto;
import gp.wagner.backend.domain.dto.response.orders.OrderAndPvRespDto;
import gp.wagner.backend.domain.dto.response.orders.OrderRespDto;
import gp.wagner.backend.domain.dto.response.PageDto;
import gp.wagner.backend.domain.entites.orders.Order;
import gp.wagner.backend.domain.entites.orders.OrderAndProductVariant;
import gp.wagner.backend.domain.exceptions.classes.ApiException;
import gp.wagner.backend.infrastructure.SimpleTuple;
import gp.wagner.backend.infrastructure.enums.sorting.GeneralSortEnum;
import gp.wagner.backend.infrastructure.enums.sorting.orders.OrdersSortEnum;
import gp.wagner.backend.middleware.Services;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/orders")
public class OrdersController {

    //Получение всех заказов
    // {{host}}/api/orders?offset=1&limit=20
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageDto<OrderRespDto> getOrderAllOrders(@Valid @RequestParam(value = "offset") @Max(100) int pageNum,
                                                   @Valid @RequestParam(value = "limit") @Max(80) int limit,
                                                   @RequestParam(value = "sort_by", defaultValue = "id")  String sortBy,
                                                   @RequestParam(value = "sort_type", defaultValue = "asc") String sortType){

        Page<Order> orderPage = Services.ordersService.getAll(pageNum, limit,
                OrdersSortEnum.getSortType(sortBy), GeneralSortEnum.getSortType(sortType));

        return new PageDto<>(
                orderPage, () -> orderPage.getContent().stream().map(OrderRespDto::new).toList()
        );
    }

    //Добавление заказа
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Long> createOrder(@Valid @RequestBody OrderRequestDto orderRequestDto){

        SimpleTuple<Long, Long> result = Services.ordersService.create(orderRequestDto);

        Map<String, Long> idAndCode = new HashMap<>();

        idAndCode.put("id", result.getValue1());
        idAndCode.put("code", result.getValue2());

        return idAndCode;
    }

    //Добавление ещё товаров в заказ/изменение кол-ва товаров в заказе
    @PutMapping(value = "/add_product_variants", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addProductVariants(@Valid @RequestBody() OrderRequestDto orderRequestDto){

        try {

            Services.ordersService.insertProductVariants(orderRequestDto);

        } catch (Exception e) {
            throw new ApiException(e.getMessage());
        }

        return new ResponseEntity<>(String.format("Вариант товаров добавлены в заказ '%d'!", orderRequestDto.getCode()), HttpStatusCode.valueOf(200)) ;
    }

    //Получение заказа по коду
    @GetMapping(value = "/order_by_code/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderRespDto getOrderByCode(@PathVariable long code){

        // Если вариант товара будет show == false, тогда на фронте нужно будет отображать эту информацию и упоминать, что сумма пересчитана
        Order foundOrder = Services.ordersService.getByOrderCode(code);

        // Если вариант будет скрыт, тогда не вводи цену, но выводи кол-во и сделай блок немного тусклым и
        // в tooltip пиши, что сумма пересчитана

        return new OrderRespDto(foundOrder);
    }

    //Получение заказов по id варианта товара
    @GetMapping(value = "/orders_by_pv", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageDto<OrderAndPvRespDto> getOrderByProductVariant(@RequestParam(value = "product_variant_id") long productVariantId,
                                                            @Valid @RequestParam(value = "offset") @Max(100) int pageNum,
                                                            @Valid @RequestParam(value = "limit") @Max(80) int limit){

        // Если вариант товара будет show == false, тогда на фронте нужно будет отображать эту информацию и упоминать, что сумма пересчитана
        Page<OrderAndProductVariant> opvListPage = Services.ordersService.getOrdersByProductVariant(productVariantId, pageNum, limit);

        if (opvListPage.getContent().isEmpty())
            throw new ApiException(String.format("Не удалось найти заказы для варианта товара с id: %d", productVariantId));

        return new PageDto<>(opvListPage, () -> opvListPage.getContent().stream().map(OrderAndPvRespDto::new).toList());
    }

    //Получение заказов по id товара
    @GetMapping(value = "/orders_for_product", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageDto<OrderRespDto> getOrderForProduct(@RequestParam(value = "productId") long productId,
                                                    @Valid @RequestParam(value = "offset") @Max(100) int pageNum,
                                                    @Valid @RequestParam(value = "limit") @Max(80) int limit,
                                                    @RequestParam(value = "sort_by", defaultValue = "id")  String sortBy,
                                                    @RequestParam(value = "sort_type", defaultValue = "asc") String sortType){

        // Если вариант товара будет show == false, тогда на фронте нужно будет отображать эту информацию и упоминать, что сумма пересчитана
        Page<Order> opvListPage = Services.ordersService.getOrdersByProductId(productId, pageNum, limit,
                OrdersSortEnum.getSortType(sortBy), GeneralSortEnum.getSortType(sortType));

        if (opvListPage.getContent().isEmpty())
            throw new ApiException(String.format("Не удалось найти заказы для товара с id: %d", productId));

        return new PageDto<>(opvListPage, () -> opvListPage.getContent().stream().map(OrderRespDto::new).toList());
    }

    //Удаление вариантов товара в определённом заказе
    @DeleteMapping(value = "/delete_by_code")
    public ResponseEntity<String> deleteProductVariantByCode(@RequestParam(value = "code") long code, @RequestParam(value = "pv_id") long productVariantId){

        boolean isDeleted = Services.ordersService.deletePVFromOrder(code, productVariantId);

        if (isDeleted)
            return new ResponseEntity<>(String.format("Вариант товара с id %d успешно удалён!", productVariantId), HttpStatusCode.valueOf(200));
        else
            return new ResponseEntity<>(String.format("Не получилось удалить вариант товара. Скорее всего не найдена запись для варианта с id %d\nЛибо заказ уже в обрабтке!",
                    productVariantId), HttpStatusCode.valueOf(500));

    }

    // Получить возможный диапазон заказов определённого статуса и/или в определённой категории
    @GetMapping(value = "/orders_dates_range", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Date> createOrder(@RequestParam(value = "state_id", required = false) Long stateId, @RequestParam(value = "category_id", required = false) Integer categoryId){

        SimpleTuple<Date, Date> result = Services.ordersService.getOrdersDatesBorders(stateId, categoryId);

        Map<String, Date> minMaxDates = new HashMap<>();

        minMaxDates.put("min", result.getValue1());
        minMaxDates.put("max", result.getValue2());

        return minMaxDates;
    }

}
