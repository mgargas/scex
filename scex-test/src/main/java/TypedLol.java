import java.util.List;
import java.util.Map;

public class TypedLol<T extends TypedLol<T>> {
    public static class Stuff<E> {

    }

    public class Dafuq<F extends List<? super String>> {
        public Map<T, F> getStuff() {
            return null;
        }
    }

}
