import org.checkerframework.checker.testchecker.wholeprograminference.qual.Parent;
import org.checkerframework.checker.testchecker.wholeprograminference.qual.Top;

public class ParameterInferenceTest {

    void test1() {
        @Parent int parent = (@Parent int) 0;
        expectsParentNoSignature(parent);
    }

    void expectsParentNoSignature(int t) {
        // :: error: (assignment.type.incompatible)
        @Parent int parent = t;
    }

    void test2() {
        @Top int top = (@Top int) 0;
        // :: error: (argument.type.incompatible)
        expectsTopNoSignature(top);
    }

    void expectsTopNoSignature(int t) {}
}
