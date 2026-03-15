package MyCPU.config

import org.chipsalliance.cde.config._

class LibraryConfig extends Field[Boolean]

case object UseNangate45 extends LibraryConfig
case object UseIcsprout55 extends LibraryConfig
case object UseCustomLibraryConfig extends LibraryConfig

class UseNangate45LibraryConfig extends Config(
    (site, here, up) => {
        case UseNangate45 => true
        case UseIcsprout55 => false
        case UseCustomLibraryConfig => false
    }
)

class UseIcsprout55LibraryConfig extends Config(
    (site, here, up) => {
        case UseNangate45 => false
        case UseIcsprout55 => true
        case UseCustomLibraryConfig => false
    }
)

class UseCustomLibraryConfig extends Config(
    (site, here, up) => {
        case UseNangate45 => false
        case UseIcsprout55 => false
        case UseCustomLibraryConfig => true
    }
)