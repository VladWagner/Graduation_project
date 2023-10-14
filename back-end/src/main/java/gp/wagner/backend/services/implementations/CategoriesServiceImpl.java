package gp.wagner.backend.services.implementations;

import gp.wagner.backend.domain.entites.categories.Category;
import gp.wagner.backend.domain.entites.categories.RepeatingCategory;
import gp.wagner.backend.domain.exception.ApiException;
import gp.wagner.backend.repositories.CategoriesRepository;
import gp.wagner.backend.repositories.SubCategoriesRepository;
import gp.wagner.backend.services.interfaces.CategoriesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoriesServiceImpl implements CategoriesService {

    //Репозиторий
    private CategoriesRepository categoriesRepository;


    private SubCategoriesRepository subCategoriesRepository;

    @Autowired
    public void setCategoriesRepository(CategoriesRepository categoriesRepository) {
        this.categoriesRepository = categoriesRepository;
    }

    @Autowired
    public void setSubCategoriesRepository(SubCategoriesRepository subCategoriesRepository) {
        this.subCategoriesRepository = subCategoriesRepository;
    }

    @Override
    //Добавление записи
    public void create(Category category){
        if(category != null)
            categoriesRepository.saveAndFlush(category);
    }

    @Override
    public long createAndCheckRepeating(String categoryName, Integer parentCategoryId) {

        //Повторяющаяся категория - родительские категории разные, а дочерние повторяются

        if (parentCategoryId == null)
            return categoriesRepository.saveAndFlush(new Category(null,categoryName,null, null)).getId();

        //Попытка найти родительскую категорию
        Category parentCategory = categoriesRepository.findById(parentCategoryId.longValue()).orElse(null);

        //Имеется ли заданная родительская категория в БД
        if (parentCategory == null)
            return categoriesRepository.saveAndFlush(new Category(null,categoryName,null, null)).getId();

        //Проверить наличие категории с такими же именем
        Category existingCategory = categoriesRepository.findCategoryByName(categoryName).orElse(null);

        //Если категория повторяется, но они принадлежат разным родительским категориям
        if(existingCategory != null && existingCategory.getParentCategory().getId() != parentCategoryId.longValue()){

            //Найти уже существующую, повторяющуюся категорию
            Optional<RepeatingCategory> subCategory = subCategoriesRepository.findRepeatingCategoryByName(categoryName);

            if (subCategory.isPresent())
                return categoriesRepository.saveAndFlush(
                        new Category(null, null, subCategory.get(),
                                parentCategory)
                ).getId();
            else
                return categoriesRepository.saveAndFlush(
                        new Category(null, null, subCategoriesRepository.saveAndFlush(new RepeatingCategory(null,categoryName)),
                                parentCategory)
                ).getId();
            //categoriesRepository.insertCategory(null, parentCategoryId, repeatingCategoryId);

        }

        //Если повторяющуюся категорию найти не удалось, или повторяющиеся категории принадлежат к разным
        return categoriesRepository.saveAndFlush(
                new Category(null, categoryName, null,
                        parentCategory)
        ).getId();

    }

    @Override
    //Изменение записи
    public void update(Category category){
        if(category != null)
            categoriesRepository.saveAndFlush(category);
    }

    public void delete(Category category) {
        if (category != null)
            categoriesRepository.delete(category);
    }

    public void deleteById(Long id) {
        if (id != null)
            categoriesRepository.deleteById(id);
    }

    @Override
    //Выборка всех категорий в БД
    public List<Category> getAll(){return categoriesRepository.findAll();}


    @Override
    //Выборка записи по id
    public Category getById(Long id){
        if (id != null)
            return categoriesRepository.findById(id).orElse(null);
        return null;
    }

    @Override
    public List<Long> getAllChildCategories(int id) {

        if (id <= 0)
            throw new ApiException(String.format("Id %d is incorrect!", id));

        return categoriesRepository.getAllChildCategoriesIds(id);
    }
    @Override
    public List<Long> getChildCategories(int id) {

        if (id <= 0)
            throw new ApiException(String.format("Id %d is incorrect!", id));

        return categoriesRepository.getChildCategoriesIds(id);
    }

}
