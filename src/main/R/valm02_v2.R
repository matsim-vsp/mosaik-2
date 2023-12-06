# add tidyverse and custom color palette which looks like google spread sheets
library(tidyverse)
library(clock)
cbPalette <- c("#4285f4", "#fbbc04", "#ea4335", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")

palm_base_parent <- read_csv("/Users/janek/Documents/palm/VALM01_v2/VALM02_v2_av_masked_M01.NO2_day2-si-units.xyt.csv") %>%
  mutate(hour = (time / 3600) + 2) # put + 2, as palm simulates in utc + 00 but the local time in summer is utc + 2
curb_curb_data_010 <- read_delim(c(
  "/Users/janek/Documents/palm/VALM01_v2/monitoring_stations/ber_mc010_20180709-20180713.csv",
  "/Users/janek/Documents/palm/VALM01_v2/monitoring_stations/ber_mc010_20180716-20180720.csv",
  "/Users/janek/Documents/palm/VALM01_v2/monitoring_stations/ber_mc010_20180723-20180726.csv"
), delim = ";", col_types = list(col_datetime("%d.%m.%Y %H:%M"), col_double(), col_double(), col_double())) %>%
  mutate(hour = get_hour(Date)) %>%
  pivot_longer(cols = c("NOx", "NO", "NO2", "PM10"), names_to = "species", values_to = "concentration")

curb_no2_010 <- curb_curb_data_010 %>%
  filter(species == "NO2") %>%
  select(Date, hour, species, concentration) %>%
  mutate(name = "010 (north)")

palm_no2_010_parent <- palm_base_parent %>%
  filter(x > 18696.0 - 17 &
           x < 18696.0 + 17 &
           y > 23768.0 - 17 &
           y < 23768.0 + 17) %>%
  mutate(concentration = value * 1e6) %>%
  mutate(name = "rand valm02") %>%
  select(hour, x, y, concentration)

p <- ggplot(palm_no2_010_parent, aes(x = hour, y = concentration)) +
  geom_line(data = curb_no2_010, aes(x = hour, y = concentration, group = day(Date), color = Date), size = 0.5, alpha = 0.5) +
  stat_summary(data = curb_no2_010, aes(x = hour, y = concentration), fun = mean, geom = "line", color = "#ea4335", size = 0.8) +
  stat_summary(fun = mean, geom = "line", color = "#fbbc04", size = 1.2) +
  stat_summary(fun.min = min, fun.max = max, fun = mean, color = "#fbbc04") +
  ggtitle("15 Days 010 NO2 and VALM02-Parent") +
  theme_light()
p