from unittest import TestCase
from niffler.Token import Token, Implementation
from niffler.NifflerSession import NifflerSession


def t3_impl_3(v1, v2):
    return [v2 for i in xrange(v1)]


class TestImplementation(TestCase):
    t1 = Token("t1", "t1uuid", "t1", "t1", "int")
    t2 = Token("t2", "t2uuid", "t2", "t2", "str")
    t3 = Token("t3", "t3uuid", "t3", "t3", "list")

    def test_execute_implementation_with_lambda(self):
        impl = self.t3.depends_on([self.t1, self.t2], lambda v1, v2: [v2 for i in xrange(v1)])
        assert isinstance(impl, Implementation)
        session = NifflerSession()
        session.set(self.t1, 5)
        session.set(self.t2, "a")
        assert impl.impl(session) == ["a", "a", "a", "a", "a"]

    @staticmethod
    def t3_impl_2(v1, v2):
        return [v2 for i in xrange(v1)]

    def test_execute_implementation_with_staticmethod(self):
        impl = self.t3.depends_on([self.t1, self.t2], self.t3_impl_2)
        assert isinstance(impl, Implementation)
        session = NifflerSession()
        session.set(self.t1, 5)
        session.set(self.t2, "a")
        assert impl.impl(session) == ["a", "a", "a", "a", "a"]

    def test_execute_implementation_with_normal_function(self):
        impl = self.t3.depends_on([self.t1, self.t2], t3_impl_3)
        assert isinstance(impl, Implementation)
        session = NifflerSession()
        session.set(self.t1, 5)
        session.set(self.t2, "a")
        assert impl.impl(session) == ["a", "a", "a", "a", "a"]
