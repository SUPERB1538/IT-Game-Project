// @GENERATOR:play-routes-compiler
// @SOURCE:/Users/wubingze/Desktop/workspace_java_backend/UofG/IT-Game-Project/conf/routes
// @DATE:Wed Mar 18 03:25:10 CST 2026


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
