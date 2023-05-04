package com.javarush.dao;

import com.javarush.domain.City;
import com.javarush.domain.Rental;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

public class RentalDAO extends GenericDAO<Rental> {
    public RentalDAO(SessionFactory sessionFactory) {
        super(Rental.class, sessionFactory);
    }

    public Rental getAnyUnreturnedRental() {
        Query<Rental> query = getCurrentSession().createQuery("SELECT r from Rental r where r.returnDate is null and r.id is not null", Rental.class);
        query.setMaxResults(1);
        return query.getSingleResult();
    }
}
