package iuh.fit.fashionwebsite.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class JPAUtil {
    private static EntityManagerFactory emf;

    static {
        // Tên "mariadb-pu" phải giống hệt trong file persistence.xml
        emf = Persistence.createEntityManagerFactory("mariadb-pu");
    }

    public static EntityManager getEntityManager() {
        return emf.createEntityManager(); // Tạo ra 1 ông quản gia mới
    }
}
