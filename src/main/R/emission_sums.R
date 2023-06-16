library(tidyverse)

cbPalette <- c("#4285f4", "#ea4335", "#fbbc04", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")

berlin_within_sums <- read_csv("C:/Users/janek/Documents/work/berlin-roadpricing/output-rp-time-berlin-100/hourly-matsim-emissions-in-filter.csv")
berlin_sums <- read_csv("C:/Users/janek/Documents/work/berlin-roadpricing/output-rp-time-berlin-100/hourly-matsim-emissions.csv")

city_within_sums <- read_csv("C:/Users/janek/Documents/work/berlin-roadpricing/output-rp-time-hundek-100/hourly-matsim-emissions-in-filter.csv")
city_sums <- read_csv("C:/Users/janek/Documents/work/berlin-roadpricing/output-rp-time-hundek-100/hourly-matsim-emissions.csv")

base_case_within_sums <- read_csv("C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/output/hourly-matsim-emissions-in-filter.csv")
base_case_sums <- read_csv("C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/output/hourly-matsim-emissions.csv")

p <- ggplot(berlin_within_sums, aes(x = time, y = sum, color = species)) +
  geom_line() +
  geom_point() +
  theme_light() +
  ggtitle("Sums for rp-time-berlin-100 - Within Berlin Boundaries.")
p

p <- ggplot(berlin_sums, aes(x = time, y = sum, color = species)) +
  geom_line() +
  geom_point() +
  theme_light() +
  ggtitle("Sums for rp-time-berlin-100 - Entire Scenario.")
p

p <- ggplot(city_within_sums, aes(x = time, y = sum, color = species)) +
  geom_line() +
  geom_point() +
  theme_light() +
  ggtitle("Sums for rp-time-city-100 - Within City Boundaries.")
p

p <- ggplot(city_sums, aes(x = time, y = sum, color = species)) +
  geom_line() +
  geom_point() +
  theme_light() +
  ggtitle("Sums for rp-time-city-100 - Entire Scenario.")
p

#join all nox datasets
joined_sums_nox <- berlin_sums %>%
  filter(species == "NOx") %>%
  inner_join(city_sums, by = c("time", "species"), suffix = c(".berlin", ".city")) %>%
  pivot_longer(cols = c("sum.berlin", "sum.city"))

joined_inner_sums_nox <- berlin_within_sums %>%
  filter(species == "NOx") %>%
  inner_join(city_within_sums, by = c("time", "species"), suffix = c(".berlin", ".city")) %>%
  pivot_longer(cols = c("sum.berlin", "sum.city"))

p <- ggplot(joined_sums_nox, aes(x = time, y = value, color = name)) +
  geom_line() +
  geom_point() +
  theme_light() +
  ggtitle("Sums NOx for rp-time-100 - Entire Scenario")
p

p <- ggplot(joined_inner_sums_nox, aes(x = time, y = value, color = name)) +
  geom_line() +
  geom_point() +
  theme_light() +
  ggtitle("Sums NOx for rp-time-100 - Within filters")
p