from unittest import TestCase
from niffler.Token import Token, Implementation
from niffler.NifflerSession import NifflerSession


def impl_3(v1, v2):
    return [v2 for i in xrange(v1)]


t1 = Token("t1", "t1uuid", "t1", "t1", "int")
t2 = Token("t2", "t2uuid", "t2", "t2", "str")


class TestImplementation(TestCase):
    def test_execute_implementation_with_lambda(self):
        impl = Implementation([t1, t2], lambda v1, v2: [v2 for i in xrange(v1)])
        assert isinstance(impl, Implementation)
        session = NifflerSession()
        session.set(t1, 5)
        session.set(t2, "a")
        assert impl.func(session) == ["a", "a", "a", "a", "a"]

    @staticmethod
    def impl_2(v1, v2):
        return [v2 for i in xrange(v1)]

    def test_execute_implementation_with_staticmethod(self):
        impl = Implementation([t1, t2], self.impl_2)
        assert isinstance(impl, Implementation)
        session = NifflerSession()
        session.set(t1, 5)
        session.set(t2, "a")
        assert impl.func(session) == ["a", "a", "a", "a", "a"]

    def test_execute_implementation_with_normal_function(self):
        impl = Implementation([t1, t2], impl_3)
        assert isinstance(impl, Implementation)
        session = NifflerSession()
        session.set(t1, 5)
        session.set(t2, "a")
        assert impl.func(session) == ["a", "a", "a", "a", "a"]
