package com.farm2home.customer.service;
import com.farm2home.customer.dto.request.LocationMasterRequest;
import com.farm2home.customer.dto.response.LocationMasterResponse;
import java.util.List;
import java.util.UUID;
public interface LocationMasterService {
 List<LocationMasterResponse> states(String search); List<LocationMasterResponse> districts(UUID stateId, String search); List<LocationMasterResponse> cities(UUID stateId, UUID districtId, String search);
 LocationMasterResponse createState(LocationMasterRequest r); LocationMasterResponse updateState(UUID id, LocationMasterRequest r); void deleteState(UUID id);
 LocationMasterResponse createDistrict(LocationMasterRequest r); LocationMasterResponse updateDistrict(UUID id, LocationMasterRequest r); void deleteDistrict(UUID id);
 LocationMasterResponse createCity(LocationMasterRequest r); LocationMasterResponse updateCity(UUID id, LocationMasterRequest r); void deleteCity(UUID id);
}
