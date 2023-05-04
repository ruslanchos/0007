package com.javarush;

import com.javarush.dao.*;
import com.javarush.domain.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class Main {
    private final SessionFactory sessionFactory;
    private final ActorDAO actorDAO;
    private final AddressDAO addressDAO;
    private final CategoryDAO categoryDAO;
    private final CityDAO cityDAO;
    private final CountryDAO countryDAO;
    private final CustomerDAO customerDAO;
    private final FilmDAO filmDAO;
    private final FilmTextDAO filmTextDAO;
    private final InventoryDAO inventoryDAO;
    private final LanguageDAO languageDAO;
    private final PaymentDAO paymentDAO;
    private final RentalDAO rentalDAO;
    private final StaffDAO staffDAO;
    private final StoreDAO storeDAO;


    public Main() {
        Properties properties = new Properties();
        properties.put(Environment.DIALECT, "org.hibernate.dialect.MySQL8Dialect");
        properties.put(Environment.DRIVER, "com.p6spy.engine.spy.P6SpyDriver");
        properties.put(Environment.URL, "jdbc:p6spy:mysql://localhost:3306/movie");
        //properties.put(Environment.DRIVER, "com.mysql.cj.jdbc.Driver");
        //properties.put(Environment.URL, "jdbc:mysql://localhost:3306/movie");
        properties.put(Environment.USER, "root");
        properties.put(Environment.PASS, "root");
        properties.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, "thread");
        properties.put(Environment.HBM2DDL_AUTO, "validate");

        sessionFactory = new Configuration()
                .addAnnotatedClass(Actor.class)
                .addAnnotatedClass(Address.class)
                .addAnnotatedClass(Category.class)
                .addAnnotatedClass(City.class)
                .addAnnotatedClass(Country.class)
                .addAnnotatedClass(Customer.class)
                .addAnnotatedClass(Film.class)
                .addAnnotatedClass(FilmText.class)
                .addAnnotatedClass(Inventory.class)
                .addAnnotatedClass(Language.class)
                .addAnnotatedClass(Payment.class)
                .addAnnotatedClass(Rental.class)
                .addAnnotatedClass(Staff.class)
                .addAnnotatedClass(Store.class)
                .addProperties(properties)
                .buildSessionFactory();

        actorDAO = new ActorDAO(sessionFactory);
        addressDAO = new AddressDAO(sessionFactory);
        categoryDAO = new CategoryDAO(sessionFactory);
        cityDAO = new CityDAO(sessionFactory);
        countryDAO = new CountryDAO(sessionFactory);
        customerDAO = new CustomerDAO(sessionFactory);
        filmDAO = new FilmDAO(sessionFactory);
        filmTextDAO = new FilmTextDAO(sessionFactory);
        inventoryDAO = new InventoryDAO(sessionFactory);
        languageDAO = new LanguageDAO(sessionFactory);
        paymentDAO = new PaymentDAO(sessionFactory);
        rentalDAO = new RentalDAO(sessionFactory);
        staffDAO = new StaffDAO(sessionFactory);
        storeDAO = new StoreDAO(sessionFactory);


    }

    public static void main(String[] args) {
        Main main = new Main();
        Customer customer = main.createCustomer();
        main.customerReturnInventoryToStore();
        main.customerRentInventory(customer);
        main.newFilmWasMade();

    }

    // Добавить транзакционный метод, который описывает событие
    // «сняли новый фильм, и он стал доступен для аренды».
    // Фильм, язык, актеров, категории и т д выбери на свое усмотрение.
    private void newFilmWasMade() {
        try(Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();

            Language language = languageDAO.getItems(0, 20).stream().unordered().findAny().get();
            List<Category> categories = categoryDAO.getItems(0, 5);
            List<Actor> actors = actorDAO.getItems(0, 5);

            Film film = new Film();
            film.setActors(new HashSet<>(actors));
            film.setRating(Rating.NC17);
            film.setSpecialFeatures(Set.of(Feature.TRAILERS, Feature.COMMENTARIES));
            film.setLength((short) 123);
            film.setReplacementCost(BigDecimal.TEN);
            film.setRentalRate(BigDecimal.ONE);
            film.setLanguage(language);
            film.setDescription("comedy");
            film.setTitle("My fairy tales");
            film.setRentalDuration((byte) 4);
            film.setOriginalLanguage(language);
            film.setCategories(new HashSet<>(categories));
            film.setYear(Year.now());
            filmDAO.save(film);

            FilmText filmText = new FilmText();
            filmText.setFilm(film);
            filmText.setId(film.getId());
            filmText.setDescription("comedy");
            filmText.setTitle("My fairy tales");
            filmTextDAO.save(filmText);


            session.getTransaction().commit();
        }
    }

    // Добавить транзакционный метод, который описывает событие
    // «покупатель сходил в магазин (store) и арендовал (rental) там инвентарь (inventory).
    // При этом он сделал оплату (payment) у продавца (staff)».
    // Фильм (через инвентарь) выбери на свое усмотрение.
    // Единственное ограничение – фильм должен быть доступен для аренды.
    // То есть либо в rental не должно быть вообще записей по инвентарю,
    // либо должна быть заполнена колонка return_date таблицы rental для последней аренды этого инвентаря.
    private void customerRentInventory(Customer customer) {
        try(Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            Film film = filmDAO.getFirstAvailableFilmForRent();
            Store store = storeDAO.getItems(0, 1).get(0); // получить магазин для покупателя

            Inventory inventory = new Inventory();
            inventory.setFilm(film);
            inventory.setStore(store);
            inventoryDAO.save(inventory);

            Staff staff = store.getStaff();

            Rental rental = new Rental();
            rental.setCustomer(customer);
            rental.setInventory(inventory);
            rental.setRentalDate(LocalDateTime.now());
            rental.setStaff(staff);
            rentalDAO.save(rental);

            Payment payment = new Payment();
            payment.setCustomer(customer);
            payment.setPaymentDate(LocalDateTime.now());
            payment.setStaff(staff);
            payment.setAmount(BigDecimal.valueOf(100.25));
            payment.setRental(rental);
            paymentDAO.save(payment);

            session.getTransaction().commit();
        }
    }

    // Транзакционный метод, который описывает событие
    // «покупатель пошел и вернул ранее арендованный фильм».
    // Покупателя и событие аренды выбери любое на свое усмотрение.
    // Рейтинг фильма пересчитывать не нужно.
    private void customerReturnInventoryToStore() {
        try(Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();

            Rental rental = rentalDAO.getAnyUnreturnedRental();
            rental.setReturnDate(LocalDateTime.now());
            rentalDAO.save(rental);

            session.getTransaction().commit();
        }
    }

    // Добавить метод, который умеет создавать нового покупателя (таблица customer)
    // со всеми зависимыми полями. Не забудь сделать чтоб метод был транзакционным
    // (чтоб не попасть в ситуацию что адрес покупателя записали в БД, а самого покупателя – нет).
    private Customer createCustomer() {
        try(Session session = sessionFactory.getCurrentSession()){
            session.beginTransaction();
            Store store = storeDAO.getItems(0, 1).get(0); // получить магазин для покупателя
            City city = cityDAO.getByName("Barcelona");

            Address address = new Address();
            address.setAddress("Freedom str, 50");
            address.setPhone("111-222-333");
            address.setCity(city);
            address.setDistrict("WildCats");

            addressDAO.save(address);

            Customer customer = new Customer();
            customer.setAddress(address);
            customer.setActive(true);
            customer.setEmail("name@mail.com");
            customer.setFirstName("John");
            customer.setLastName("Wood");
            customer.setStore(store);
            customerDAO.save(customer);

            session.getTransaction().commit();
            return customer;
        }
    }
}
