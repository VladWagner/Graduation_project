package gp.wagner.backend.domain.dto.response.orders;

import com.fasterxml.jackson.annotation.JsonProperty;
import gp.wagner.backend.domain.entites.orders.Customer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO для отправки на сторону клиента
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRespDto {

    private Long id;

    // Посетитель, перешедший в разряд покупателя

    //Фамилия
    private String surname;

    //Имя
    private String name;

    //Отчество
    private String patronymic;

    //Email
    private String email;

    //Номер телефона
    @JsonProperty(value = "phone_number")
    private long phoneNumber;

    // Посетитель, с которого пришел заказ
    @JsonProperty(value = "visitor_finger_print")
    private String visitorFingerPrint;

    public CustomerRespDto(Customer customer) {
        this.id = customer.getId();
        this.surname = customer.getSurname();
        this.name = customer.getName();
        this.patronymic = customer.getPatronymic();
        this.email = customer.getEmail();
        this.phoneNumber = customer.getPhoneNumber();
        this.visitorFingerPrint = customer.getVisitor().getFingerprint();
    }
}
