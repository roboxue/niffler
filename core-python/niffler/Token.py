class Token:
    type_name = None  # type: str
    code_name = None  # type: str
    uuid = None  # type: str
    summary_name = None  # type: str
    description = None  # type: str

    def __init__(self, code_name, uuid, summary_name, description, type_name):
        self.description = description
        self.summary_name = summary_name
        self.uuid = uuid
        self.code_name = code_name
        self.type_name = type_name

    def depends_on(self, tokens, impl):
        """
        :type tokens: Sequence[[Token]]
        :type impl: Callable[[t.type_name for t in tokens], self.type_name]
        :return:
        """
        def _impl_(session):
            return impl(*[session.get(t) for t in tokens])
        return Implementation(self, tokens, _impl_)


class Implementation:
    impl = None  # type: function
    fulfill = None  # type: Token
    dependencies = None  # type: list

    def __init__(self, fulfill, dependencies, impl):
        self.dependencies = dependencies
        self.fulfill = fulfill
        self.impl = impl


