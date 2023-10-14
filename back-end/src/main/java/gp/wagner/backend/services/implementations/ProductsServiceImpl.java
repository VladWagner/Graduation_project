package gp.wagner.backend.services.implementations;

import gp.wagner.backend.domain.dto.request.crud.product.ProductDto;
import gp.wagner.backend.domain.dto.request.filters.ProductFilterDtoContainer;
import gp.wagner.backend.domain.entites.categories.Category;
import gp.wagner.backend.domain.entites.products.Product;
import gp.wagner.backend.domain.specifications.ProductSpecifications;
import gp.wagner.backend.infrastructure.ServicesUtils;
import gp.wagner.backend.infrastructure.SimpleTuple;
import gp.wagner.backend.repositories.ProductsRepository;
import gp.wagner.backend.services.interfaces.ProductsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

//Сервис для таблицы товаров
@Service
public class ProductsServiceImpl implements ProductsService {

    //Репозиторий
    private ProductsRepository productsRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public void setProductsRepository(ProductsRepository prodRepo) {
        this.productsRepository = prodRepo;
    }

    //region Добавление
    @Override
    //Добавление записи
    public void create(Product product){
        if(product == null)
            return;

        productsRepository.saveAndFlush(product);
    }

    //Добавление товара из DTO
    @Override
    public long create(ProductDto dto) {
      if (dto == null)
          return -1;

        productsRepository.insertProduct(dto.getName(), dto.getDescription(),dto.getCategoryId().intValue(),
                dto.getProducerId().intValue(),
                dto.getIsAvailable() ? 1 : 0, dto.getShowProduct() ? 1 : 0);

        return productsRepository.getMaxId();
    }
    //endregion


    //region Изменение
    @Override
    public void update(Product item) {
        if(item == null)
            return;

        productsRepository.saveAndFlush(item);

    }

    //Из DTO
    @Override
    public void update(ProductDto dto) {
        if (dto == null)
            return;

        productsRepository.updateProduct(dto.getId(), dto.getName(), dto.getDescription(),
                dto.getCategoryId(), dto.getProducerId(),
                dto.getIsAvailable() ? 1 : 0, dto.getShowProduct() ? 1 : 0);
    }
    //endregion

    @Override
    //Выборка всех записей
    @Transactional(readOnly = true)
    public List<Product> getAll(){return productsRepository.findAll();}

    @Override
    public long getMaxId() {
        return productsRepository.getMaxId();
    }

    //Выборка с пагинацией
    @Override
    @Transactional()
    public Page<Product> getAll(int pageNum, int dataOnPage) {

        return productsRepository.findAll(PageRequest.of(pageNum, dataOnPage));
    }

    //Фильтрация и пагинация
    @Override
    public SimpleTuple<List<Product>, Integer> getAll(ProductFilterDtoContainer container, Long categoryId, String priceRange, int pageNum, int dataOnPage) {

        //Сформировать набор спецификаций для выборки из набора фильтров (фильтр = атрибут (характеристика) + операция)
        List<Specification<Product>> specifications = ProductSpecifications.createNestedProductSpecifications(container);

        //Объект для формирования запросов - построитель запроса
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        //Рассчитать общее кол-во данных с такими фильтрами
        int totalCount = countData(cb, specifications, categoryId, priceRange);

        CriteriaQuery<Product> query = cb.createQuery(Product.class);

        //Получить таблицу товаров для запросов
        Root<Product> root = query.from(Product.class);

        List<Predicate> predicates = new ArrayList<>();

        //Доп.фильтрация по категории
        if (categoryId != null) {
            //Присоединить сущность категорий
            Join<Product, Category> categoryJoin = root.join("category");

            //Задать доп.условие выборки - по категориям
            predicates.add(cb.equal(categoryJoin.get("id"), categoryId));
        }

        //Доп.фильтрация по ценам
        if (priceRange != null)
            predicates.add(ServicesUtils.getPricePredicate(priceRange, root, query, cb));

        //Получить предикат для выборки по заданным фильтрам (контейнер фильтров, который был передан из контроллера)
        //При помощи данного предиката запросы будут строится со стороны Spring data & Hibernate
        //Метод toPredicate - по-идее использует то анонимное создание спецификации, которое  задал в ProductSpecifications.java
        Predicate filterPredicate = Specification.allOf(specifications).toPredicate(root, query, cb);

        //Сформировать запрос
        if (predicates.size() > 0)
            //Доп.фильтра + фильтра по характеристикам
            query.where(cb.and(
                    cb.and(predicates.toArray(new Predicate[0])), filterPredicate));
        else
            query.where(filterPredicate);

        if (pageNum > 0)
            pageNum -= 1;

        //Данный объект нужен для пагинации полученных после выборки результатов
        TypedQuery<Product> typedQuery = entityManager.createQuery(query);

        //region Пагинация через TypedQuery
        //Задать кол-во результатов на странице
        //typedQuery.setMaxResults(dataOnPage);

        //Задать смещение на кол-во страниц
        //typedQuery.setFirstResult(pageNum*dataOnPage);
        //endregion

        List<Product> products = typedQuery.getResultList();

        //Пагинация готовой коллекции
        int listLength = products.size();

        //Начало списка - вычисляем мин.значения из двух во избежание выхода за пределы списка
        int startIdx = Math.min(pageNum*dataOnPage, listLength);

        //Конец списка (offset + dataOnMage) - вычисляем мин.значения из двух во избежание выхода за пределы списка
        int endIdx = Math.min(startIdx + dataOnPage, listLength);

        return new SimpleTuple<>(products.subList(startIdx, endIdx), totalCount);
        //return new SimpleTuple<>(products, totalCount);
    }


    //Метод для подсчёта кол-ва данных полученных после выборки по определённым фильтрам.
    //То есть здесь мы по сути производим повторный запрос с теми же фильтрами и считаем кол-во данных полученных с нег
    private int countData(CriteriaBuilder cb, List<Specification<Product>> specifications, Long categoryId, String priceRange){

        CriteriaQuery<Long> query = cb.createQuery(Long.class);

        //Получить таблицу для запросов
        Root<Product> root = query.from(Product.class);

        Predicate filter = Specification.allOf(specifications).toPredicate(root, query, cb);

        List<Predicate> predicates = new ArrayList<>();
        if (categoryId != null) {
            //Присоединить сущность категорий
            Join<Product, Category> categoryJoin = root.join("category");

            //Задать доп.условие выборки - по категориям
            predicates.add(cb.equal(categoryJoin.get("id"), categoryId));
        }

        //Доп.фильтрация по ценам
        if (priceRange != null)
            predicates.add(ServicesUtils.getPricePredicate(priceRange, root, query, cb));

        //Сформировать запрос с подсчётом кол-ва записей
        if (predicates.size() > 0)
            query.select(cb.countDistinct(root)).where(cb.and(predicates.toArray(new Predicate[0])), filter);
        else
            query.select(cb.countDistinct(root)).where(filter);

        TypedQuery<Long> typedQuery = entityManager.createQuery(query);

        Long result = typedQuery.getSingleResult();

        //Интересно, на этом моменте происходит этот огромный запрос к БД или всё же он как-то это оптимизирует
        return result.intValue();
    }

    @Override
    //Выборка записи по id
    public Product getById(Long id){
        if (id != null) {
            Optional<Product> productOptional = productsRepository.findById(id);

            return productOptional.orElse(null);

        }
        return null;
    }

    @Override
    public Page<Product> getByCategory(long categoryId, int pageNum, int dataOnPage) {



        return productsRepository.findProductsByCategoryId(categoryId, PageRequest.of(pageNum, dataOnPage));
    }

    //Посчитать, сколько записей в каждой категории
    @Override
    public int countByCategory(long categoryId) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> query = cb.createQuery(Long.class);

        //Таблица товаров
        Root<Product> root = query.from(Product.class);

        //Присоединяем таблицу категорий
        Join<Product, Category> categoryJoin = root.join("category");

        //Посчитать кол-во товаров, где id категории = заданному
        query.select(cb.count(root)).where(cb.equal(categoryJoin.get("id"), categoryId));

        return entityManager.createQuery(query).getSingleResult().intValue();
    }

}