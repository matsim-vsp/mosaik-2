require(matsim)
library(matsim)
library(dplyr)
library(sf)

policy_1 <- matsim::readTripsTable("C:/Users/Janekdererste/Documents/work/berlin-roadpricing/output_roadpricing_mc/berlin-with-geometries-roadpricing-mode-choice-1.output_trips.csv.gz")
policy_100 <- matsim::readTripsTable("C:/Users/Janekdererste/Documents/work/berlin-roadpricing/output_roadpricing_mc/berlin-with-geometries-roadpricing-mode-choice-100.output_trips.csv.gz")
policy_10000 <- matsim::readTripsTable("C:/Users/Janekdererste/Documents/work/berlin-roadpricing/output_roadpricing_mc/berlin-with-geometries-roadpricing-mode-choice-10000.output_trips.csv.gz")
base <- matsim::readTripsTable("C:/Users/Janekdererste/Documents/work/berlin-roadpricing/output_roadpricing_mc/berlin-with-geometry-attributes.output_trips.csv.gz")

shp <- st_read("D:/svn/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/input/berlin-shp/berlin.shp")

matsim::compareTripTypesBarChart(base, policy_10000, shp, crs = "EPSG:25833")
