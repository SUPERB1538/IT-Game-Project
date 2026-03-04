// @GENERATOR:play-routes-compiler
// @SOURCE:/Users/wubingze/Desktop/workspace_java_backend/UofG/IT-Game-Project/conf/routes
// @DATE:Thu Feb 26 07:34:42 CST 2026


package router {
  object RoutesPrefix {
    private var _prefix: String = "/"
    def setPrefix(p: String): Unit = {
      _prefix = p
    }
    def prefix: String = _prefix
    val byNamePrefix: Function0[String] = { () => prefix }
  }
}
