class Token:
    def __init__(self, code_name, uuid, summary_name, description, type_name):
        self.description = description
        self.summary_name = summary_name
        self.uuid = uuid
        self.code_name = code_name
        self.type_name = type_name


class Implementation:
    def __init__(self, dependencies, func):
        self.dependencies = dependencies

        def impl(session):
            return func(*[session.get(t) for t in dependencies])

        self.func = impl
