package com.farm2home.customer.service.impl;

import com.farm2home.common.web.exception.ConflictException;
import com.farm2home.customer.domain.entity.*;
import com.farm2home.customer.domain.repository.*;
import com.farm2home.customer.dto.request.LocationMasterRequest;
import com.farm2home.customer.dto.response.LocationMasterResponse;
import com.farm2home.customer.exception.ResourceNotFoundException;
import com.farm2home.customer.service.LocationMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.*;
import java.util.function.Predicate;

@Service @RequiredArgsConstructor @Transactional
public class LocationMasterServiceImpl implements LocationMasterService {
 private final LocationStateRepository states; private final LocationDistrictRepository districts; private final LocationCityRepository cities;
 @Transactional(readOnly=true) public List<LocationMasterResponse> states(String search) { return states.findAll().stream().filter(matches(search, LocationState::getName)).sorted(Comparator.comparing(LocationState::getName)).map(this::state).toList(); }
 @Transactional(readOnly=true) public List<LocationMasterResponse> districts(UUID stateId, String search) { return (stateId == null ? districts.findAll() : districts.findAllByStateIdOrderByNameAsc(stateId)).stream().filter(matches(search, LocationDistrict::getName)).map(d -> district(d, getState(d.getStateId()))).toList(); }
 @Transactional(readOnly=true) public List<LocationMasterResponse> cities(UUID stateId, UUID districtId, String search) { return cities.findAll().stream().filter(c -> districtId == null || c.getDistrictId().equals(districtId)).filter(c -> stateId == null || getDistrict(c.getDistrictId()).getStateId().equals(stateId)).filter(matches(search, LocationCity::getName)).map(c -> city(c, getDistrict(c.getDistrictId()))).toList(); }
 public LocationMasterResponse createState(LocationMasterRequest r) { duplicate(states.existsByNameIgnoreCase(r.getName()) || states.existsByCodeIgnoreCase(r.getCode())); return state(states.save(LocationState.builder().name(r.getName().trim()).code(r.getCode().trim()).countryCode("IN").active(r.getActive()).build())); }
 public LocationMasterResponse updateState(UUID id, LocationMasterRequest r) { LocationState s=getState(id); if ((!s.getName().equalsIgnoreCase(r.getName()) && states.existsByNameIgnoreCase(r.getName())) || (!s.getCode().equalsIgnoreCase(r.getCode()) && states.existsByCodeIgnoreCase(r.getCode()))) duplicate(true); s.setName(r.getName().trim()); s.setCode(r.getCode().trim()); s.setActive(r.getActive()); return state(states.save(s)); }
 public void deleteState(UUID id) { if (districts.existsByStateIdAndActiveTrue(id)) throw new ConflictException("Cannot delete a state with active districts."); states.delete(getState(id)); }
 public LocationMasterResponse createDistrict(LocationMasterRequest r) { LocationState s=activeState(r.getStateId()); if (districts.existsByStateIdAndNameIgnoreCase(s.getId(),r.getName()) || districts.existsByCodeIgnoreCase(r.getCode())) duplicate(true); return district(districts.save(LocationDistrict.builder().stateId(s.getId()).name(r.getName().trim()).code(r.getCode().trim()).active(r.getActive()).build()),s); }
 public LocationMasterResponse updateDistrict(UUID id, LocationMasterRequest r) { LocationDistrict d=getDistrict(id); LocationState s=activeState(r.getStateId()); if (districts.existsByStateIdAndNameIgnoreCaseAndIdNot(s.getId(),r.getName(),id) || (!d.getCode().equalsIgnoreCase(r.getCode()) && districts.existsByCodeIgnoreCase(r.getCode()))) duplicate(true); d.setStateId(s.getId());d.setName(r.getName().trim());d.setCode(r.getCode().trim());d.setActive(r.getActive()); return district(districts.save(d),s); }
 public void deleteDistrict(UUID id) { if(cities.existsByDistrictIdAndActiveTrue(id)) throw new ConflictException("Cannot delete a district with active cities."); districts.delete(getDistrict(id)); }
 public LocationMasterResponse createCity(LocationMasterRequest r) { LocationDistrict d=activeDistrict(r.getDistrictId()); if(cities.existsByDistrictIdAndNameIgnoreCase(d.getId(),r.getName())) duplicate(true); return city(cities.save(LocationCity.builder().districtId(d.getId()).name(r.getName().trim()).active(r.getActive()).build()),d); }
 public LocationMasterResponse updateCity(UUID id, LocationMasterRequest r) { LocationCity c=getCity(id); LocationDistrict d=activeDistrict(r.getDistrictId()); if(cities.existsByDistrictIdAndNameIgnoreCaseAndIdNot(d.getId(),r.getName(),id)) duplicate(true); c.setDistrictId(d.getId());c.setName(r.getName().trim());c.setActive(r.getActive()); return city(cities.save(c),d); }
 public void deleteCity(UUID id) { cities.delete(getCity(id)); }
 private LocationState getState(UUID id){return states.findById(id).orElseThrow(()->new ResourceNotFoundException("State not found: "+id));} private LocationState activeState(UUID id){LocationState s=getState(id);if(!s.isActive()) throw new ConflictException("An inactive state cannot be selected.");return s;} private LocationDistrict getDistrict(UUID id){return districts.findById(id).orElseThrow(()->new ResourceNotFoundException("District not found: "+id));} private LocationDistrict activeDistrict(UUID id){LocationDistrict d=getDistrict(id);if(!d.isActive())throw new ConflictException("An inactive district cannot be selected.");return d;} private LocationCity getCity(UUID id){return cities.findById(id).orElseThrow(()->new ResourceNotFoundException("City not found: "+id));}
 private LocationMasterResponse state(LocationState s){return LocationMasterResponse.builder().id(s.getId()).name(s.getName()).code(s.getCode()).active(s.isActive()).build();} private LocationMasterResponse district(LocationDistrict d,LocationState s){return LocationMasterResponse.builder().id(d.getId()).name(d.getName()).code(d.getCode()).active(d.isActive()).stateId(s.getId()).stateName(s.getName()).build();} private LocationMasterResponse city(LocationCity c,LocationDistrict d){LocationState s=getState(d.getStateId());return LocationMasterResponse.builder().id(c.getId()).name(c.getName()).active(c.isActive()).districtId(d.getId()).districtName(d.getName()).stateId(s.getId()).stateName(s.getName()).build();}
 private void duplicate(boolean yes){if(yes)throw new ConflictException("A location with the same name or code already exists.");} private <T> Predicate<T> matches(String q, java.util.function.Function<T,String> n){return v->!StringUtils.hasText(q)||n.apply(v).toLowerCase().contains(q.trim().toLowerCase());}
}
